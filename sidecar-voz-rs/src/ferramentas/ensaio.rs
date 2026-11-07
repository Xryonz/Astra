use super::bancada::Bancada;
use super::medidas::{Custos, ResumoDosCustos, arredondar, tempo_de_cpu};
use super::relogio::Relogio;
use crate::tela::{
    Aceitos, Compressor, Configuracao, Entrada, Evento, Perfil, Placa, Unidade, amostra_da_textura,
    codificadores_h264_da_placa, dispositivo_da_placa, em, ler_unidade,
};
use serde::Serialize;
use std::ops::Range;
use std::sync::mpsc::{self, RecvTimeoutError};
use std::time::{Duration, Instant};
use windows::Win32::Media::MediaFoundation::IMFSample;

const LARGURA: u32 = 1920;
const ALTURA: u32 = 1080;
const FPS: u32 = 60;
const QUADROS: u32 = 300;
const KBPS_ANTES: u32 = 8000;
const KBPS_DEPOIS: u32 = 4000;
const QUADRO_DA_TROCA_DE_TAXA: u32 = 120;
const QUADRO_CHAVE_PEDIDO: u32 = 150;
const JANELA_ANTES: Range<u32> = 0..120;
const JANELA_DEPOIS: Range<u32> = 180..300;
const LIMITE: Duration = Duration::from_secs(30);

#[derive(Serialize)]
#[serde(untagged)]
pub enum ResultadoDoEnsaio {
    Feito(Box<Ensaio>),
    Falhou { entrada: &'static str, relogio: &'static str, erro: String },
}

#[derive(Serialize)]
pub struct Ensaio {
    entrada: &'static str,
    relogio: &'static str,
    codificador: String,
    aceitos: Aceitos,
    perfil_pedido: u32,
    sps: Option<Perfil>,
    saidas: usize,
    idr: Vec<u32>,
    idr_sem_parametros: usize,
    quadros_b: usize,
    quadro_chave: QuadroChave,
    taxa: Taxa,
    custo_us: ResumoDosCustos,
    cpu_ms_por_quadro: f64,
    quadros_por_segundo: f64,
}

#[derive(Serialize)]
struct QuadroChave {
    pedido_em: u32,
    aceito: bool,
    veio_em: Option<u32>,
}

#[derive(Serialize)]
struct Taxa {
    alvo_antes: u32,
    antes_kbps: f64,
    alvo_depois: u32,
    depois_kbps: f64,
    troca_aceita: bool,
    kbps_por_segundo: Vec<f64>,
}

struct Saida {
    quadro: u32,
    bytes: usize,
    unidade: Unidade,
}

pub fn ensaiar_com_limite(placa: &Placa, entrada: Entrada, relogio: Relogio) -> ResultadoDoEnsaio {
    let (_feito, espera) = mpsc::channel::<()>();
    std::thread::spawn(move || {
        if espera.recv_timeout(LIMITE) == Err(RecvTimeoutError::Timeout) {
            eprintln!("o ensaio {} travou: o codificador parou de responder", entrada.nome());
            std::process::exit(3);
        }
    });
    match ensaiar(placa, entrada, relogio) {
        Ok(ensaio) => ResultadoDoEnsaio::Feito(Box::new(ensaio)),
        Err(erro) => ResultadoDoEnsaio::Falhou { entrada: entrada.nome(), relogio: relogio.nome(), erro },
    }
}

fn ensaiar(placa: &Placa, entrada: Entrada, relogio: Relogio) -> Result<Ensaio, String> {
    let _relogio = relogio.valer();
    let codificador = codificadores_h264_da_placa(placa.luid)
        .map_err(em("listar os codificadores"))?
        .into_iter()
        .next()
        .ok_or("a placa não tem codificador H.264")?;
    let dispositivo = dispositivo_da_placa(&placa.adaptador).map_err(em("criar o dispositivo na placa"))?;
    let config = Configuracao { largura: LARGURA, altura: ALTURA, fps: FPS, kbps: KBPS_ANTES, entrada };
    let transformacao = codificador.ativar().map_err(em("ligar o codificador"))?;
    let compressor = Compressor::abrir(transformacao, &dispositivo.placa, config)?;
    let bancada = Bancada::montar(&dispositivo, entrada, LARGURA, ALTURA).map_err(em("montar a bancada"))?;
    let amostra = amostra_da_textura(bancada.entrada()).map_err(em("embrulhar a textura"))?;
    let mut rodada = Rodada::nova(compressor, bancada, amostra);
    let cpu_antes = tempo_de_cpu();
    let comeco = Instant::now();
    rodada.rodar()?;
    let parede = comeco.elapsed();
    let cpu = tempo_de_cpu().saturating_sub(cpu_antes);
    Ok(rodada.relatar(entrada, relogio, &codificador.nome, parede, cpu))
}

struct Rodada {
    compressor: Compressor,
    bancada: Bancada,
    amostra: IMFSample,
    custos: Custos,
    saidas: Vec<Saida>,
    pedidos: u32,
    proximo: u32,
    entregue_em: Option<Instant>,
    troca_aceita: bool,
    chave_aceita: bool,
}

impl Rodada {
    fn nova(compressor: Compressor, bancada: Bancada, amostra: IMFSample) -> Self {
        Self {
            compressor,
            bancada,
            amostra,
            custos: Custos::default(),
            saidas: Vec::with_capacity(QUADROS as usize),
            pedidos: 0,
            proximo: 0,
            entregue_em: None,
            troca_aceita: false,
            chave_aceita: false,
        }
    }

    fn rodar(&mut self) -> Result<(), String> {
        let mut drenando = false;
        loop {
            let livre = self.entregue_em.is_none();
            if livre && self.pedidos > 0 && self.proximo < QUADROS {
                self.alimentar()?;
            } else if livre && self.proximo == QUADROS && !drenando {
                self.compressor.drenar().map_err(em("drenar o codificador"))?;
                drenando = true;
            }
            if !self.receber()? {
                return Ok(());
            }
        }
    }

    fn alimentar(&mut self) -> Result<(), String> {
        let quadro = self.proximo;
        let comeco = Instant::now();
        self.bancada.pintar(quadro);
        let pintado = Instant::now();
        self.bancada.preparar().map_err(em("preparar o quadro"))?;
        let preparado = Instant::now();
        self.seguir_o_roteiro(quadro);
        let antes_de_entregar = Instant::now();
        self.compressor.entregar(&self.amostra, quadro).map_err(em("entregar o quadro"))?;
        let entregue = Instant::now();
        self.custos.desenho.push(pintado - comeco);
        self.custos.preparo.push(preparado - pintado);
        self.custos.entrega.push(entregue - antes_de_entregar);
        self.entregue_em = Some(entregue);
        self.pedidos -= 1;
        self.proximo += 1;
        Ok(())
    }

    fn seguir_o_roteiro(&mut self, quadro: u32) {
        if quadro == QUADRO_DA_TROCA_DE_TAXA {
            self.troca_aceita = self.compressor.mudar_taxa(KBPS_DEPOIS);
        }
        if quadro == QUADRO_CHAVE_PEDIDO {
            self.chave_aceita = self.compressor.pedir_quadro_chave();
        }
    }

    fn receber(&mut self) -> Result<bool, String> {
        match self.compressor.proximo_evento()? {
            Evento::PedeEntrada => self.pedidos += 1,
            Evento::Saida { tempo, bytes, chegou, custo } => {
                if let Some(entregue) = self.entregue_em.take() {
                    self.custos.latencia.push(chegou - entregue);
                }
                self.custos.saida.push(custo);
                let quadro = tempo.map_or(self.saidas.len() as u32, |tempo| self.compressor.quadro_do_tempo(tempo));
                self.saidas.push(Saida { quadro, bytes: bytes.len(), unidade: ler_unidade(&bytes) });
            }
            Evento::FimDoDreno => return Ok(false),
            Evento::Nenhum => {}
        }
        Ok(true)
    }

    fn kbps_em(&self, janela: Range<u32>) -> f64 {
        let segundos = f64::from(janela.end - janela.start) / f64::from(FPS);
        let bytes: usize = self.saidas.iter().filter(|saida| janela.contains(&saida.quadro)).map(|saida| saida.bytes).sum();
        arredondar(bytes as f64 * 8.0 / segundos / 1000.0)
    }

    fn relatar(&self, entrada: Entrada, relogio: Relogio, codificador: &str, parede: Duration, cpu: Duration) -> Ensaio {
        let idr: Vec<u32> = self.saidas.iter().filter(|saida| saida.unidade.idr).map(|saida| saida.quadro).collect();
        let sem_parametros = |saida: &&Saida| saida.unidade.idr && !(saida.unidade.sps && saida.unidade.pps);
        Ensaio {
            entrada: entrada.nome(),
            relogio: relogio.nome(),
            codificador: codificador.to_owned(),
            aceitos: self.compressor.aceitos,
            perfil_pedido: self.compressor.perfil_pedido(),
            sps: self.saidas.iter().find_map(|saida| saida.unidade.perfil),
            saidas: self.saidas.len(),
            quadro_chave: QuadroChave {
                pedido_em: QUADRO_CHAVE_PEDIDO,
                aceito: self.chave_aceita,
                veio_em: idr.iter().copied().find(|&quadro| quadro >= QUADRO_CHAVE_PEDIDO),
            },
            idr,
            idr_sem_parametros: self.saidas.iter().filter(sem_parametros).count(),
            quadros_b: self.saidas.iter().filter(|saida| saida.unidade.quadro_b).count(),
            taxa: Taxa {
                alvo_antes: KBPS_ANTES,
                antes_kbps: self.kbps_em(JANELA_ANTES),
                alvo_depois: KBPS_DEPOIS,
                depois_kbps: self.kbps_em(JANELA_DEPOIS),
                troca_aceita: self.troca_aceita,
                kbps_por_segundo: (0..QUADROS / FPS).map(|segundo| self.kbps_em(segundo * FPS..(segundo + 1) * FPS)).collect(),
            },
            custo_us: self.custos.resumir(),
            cpu_ms_por_quadro: arredondar(cpu.as_secs_f64() * 1000.0 / f64::from(QUADROS)),
            quadros_por_segundo: arredondar(f64::from(QUADROS) / parede.as_secs_f64()),
        }
    }
}
