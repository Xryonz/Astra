use super::PedidoDeTela;
use super::ritmo::Ritmo;
use super::ritmo_que_cabe::RitmoQueCabe;
use crate::com::ComDaThread;
use crate::ponte::{Escritor, Evento};
use crate::tela::{
    Aceitos, Bancada, CapturaDeJanela, Codificacao, Colheita, Configuracao, Entrada, Evento as DoCodificador,
    Origem, Unidade, abrir_codificacao, em, ler_unidade, placas_de_hardware,
};
use livekit::webrtc::video_frame::{EncodedFrameType, EncodedVideoCodec, EncodedVideoFrame};
use livekit::webrtc::video_source::VideoResolution;
use livekit::webrtc::video_source::native::NativeVideoSource;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use windows::Win32::Foundation::HWND;
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_FULL, MFShutdown, MFStartup};

const ESPERA_POR_QUADRO_NOVO: Duration = Duration::from_millis(100);
const SINAL_DE_VIDA: Duration = Duration::from_secs(2);

enum Fonte {
    Sintetica,
    Janela(CapturaDeJanela),
}

pub fn transmitir(pedido: PedidoDeTela, fonte: NativeVideoSource, escritor: Arc<Escritor>, parar: Arc<AtomicBool>) {
    let _com = ComDaThread::iniciar();
    if let Err(msg) = transmitir_com_o_media_foundation(pedido, fonte, &escritor, parar) {
        escritor.manda(&Evento::erro(format!("transmissão parou: {msg}")));
    }
    escritor.manda(&Evento::transmissao_encerrada());
}

fn transmitir_com_o_media_foundation(
    pedido: PedidoDeTela,
    fonte: NativeVideoSource,
    escritor: &Arc<Escritor>,
    parar: Arc<AtomicBool>,
) -> Result<(), String> {
    unsafe { MFStartup(MF_VERSION, MFSTARTUP_FULL) }.map_err(em("iniciar o Media Foundation"))?;
    let feito = Laco::abrir(pedido, fonte, Arc::clone(escritor), parar).and_then(|mut laco| laco.rodar());
    let _ = unsafe { MFShutdown() };
    feito
}

struct Laco {
    fonte: Fonte,
    codificacao: Codificacao,
    saida: NativeVideoSource,
    escritor: Arc<Escritor>,
    parar: Arc<AtomicBool>,
    resolucao: VideoResolution,
    comeco: Instant,
    fps: u32,
    intervalo: Duration,
    cabe: RitmoQueCabe,
    teto_kbps: u32,
    kbps: u32,
    pedidos: u32,
    quadro: u32,
    entregue_em: Option<Instant>,
    ultima_saida: Instant,
    vez: Instant,
    chave_pendente: bool,
    tem_imagem: bool,
    perfil_anunciado: bool,
    ritmo: Ritmo,
}

impl Laco {
    fn abrir(
        pedido: PedidoDeTela,
        saida: NativeVideoSource,
        escritor: Arc<Escritor>,
        parar: Arc<AtomicBool>,
    ) -> Result<Self, String> {
        let config = Configuracao {
            largura: pedido.largura,
            altura: pedido.altura,
            fps: pedido.fps,
            kbps: pedido.kbps,
            entrada: Entrada::Argb,
        };
        let origem = if pedido.janela.is_some() { Origem::Capturada } else { Origem::Sintetica };
        let codificacao = codificacao_na_primeira_placa(config, origem)?;
        let fonte = match pedido.janela {
            Some(janela) => Fonte::Janela(
                CapturaDeJanela::abrir(codificacao.placa(), HWND(janela as *mut _))
                    .map_err(em("abrir a captura da janela"))?,
            ),
            None => Fonte::Sintetica,
        };
        let resumo = como_subiu(&pedido, codificacao.compressor.aceitos, &fonte);
        escritor.manda(&Evento::transmissao(&codificacao.nome, resumo));
        let agora = Instant::now();
        Ok(Self {
            fonte,
            codificacao,
            saida,
            escritor,
            parar,
            resolucao: VideoResolution { width: pedido.largura, height: pedido.altura },
            comeco: agora,
            fps: pedido.fps,
            intervalo: Duration::from_secs(1) / pedido.fps,
            cabe: RitmoQueCabe::novo(pedido.fps),
            teto_kbps: pedido.kbps,
            kbps: pedido.kbps,
            pedidos: 0,
            quadro: 0,
            entregue_em: None,
            ultima_saida: agora,
            vez: agora,
            chave_pendente: false,
            tem_imagem: false,
            perfil_anunciado: false,
            ritmo: Ritmo::novo(),
        })
    }

    fn rodar(&mut self) -> Result<(), String> {
        while !self.parar.load(Ordering::Relaxed) {
            self.fechar_o_segundo();
            if self.entregue_em.is_none() && self.pedidos > 0 {
                self.esperar_a_vez();
                self.atender_a_rede();
                if !self.alimentar()? {
                    continue;
                }
            }
            self.receber()?;
        }
        Ok(())
    }

    fn fechar_o_segundo(&mut self) {
        let Some(segundo) = self.ritmo.fechar_o_segundo(self.kbps) else { return };
        self.escritor.manda(&Evento::transmissao("ritmo", segundo.resumo));
        if let Some(trabalho) = segundo.trabalho_por_quadro {
            self.seguir_a_maquina(trabalho);
        }
    }

    fn seguir_a_maquina(&mut self, trabalho: Duration) {
        let Some(fps) = self.cabe.segundo(trabalho, self.fps) else { return };
        let rumo = if fps > self.fps { "subindo" } else { "caindo" };
        self.fps = fps;
        self.intervalo = Duration::from_secs(1) / fps;
        self.codificacao.compressor.ajustar_quadros(fps);
        let milissegundos = trabalho.as_secs_f64() * 1000.0;
        let recado = format!("{milissegundos:.1}ms de trabalho por quadro nesta máquina; {rumo} para {fps}/s");
        self.escritor.manda(&Evento::transmissao("taxa", recado));
    }

    fn esperar_a_vez(&mut self) {
        if let Some(falta) = self.vez.checked_duration_since(Instant::now()) {
            std::thread::sleep(falta);
        }
        let agora = Instant::now();
        let atrasou = agora.saturating_duration_since(self.vez) > self.intervalo;
        self.vez = if atrasou { agora } else { self.vez } + self.intervalo;
    }

    fn atender_a_rede(&mut self) {
        if self.saida.take_keyframe_request() {
            self.ritmo.contar_pedido_de_chave();
            self.codificacao.compressor.pedir_quadro_chave();
            self.chave_pendente = true;
        }
        if let Some(pedido) = self.saida.take_rate_control_request() {
            self.seguir_a_rede(pedido.target_bitrate_bps);
        }
    }

    fn seguir_a_rede(&mut self, bps: u64) {
        if bps == 0 {
            return;
        }
        let kbps = (bps / 1000).clamp(1, u64::from(self.teto_kbps)) as u32;
        self.ritmo.anotar_alvo_da_rede(kbps);
        if kbps != self.kbps && self.codificacao.compressor.mudar_taxa(kbps) {
            self.kbps = kbps;
        }
    }

    fn alimentar(&mut self) -> Result<bool, String> {
        let preparo = match self.preparar_o_quadro()? {
            Some(gasto) => {
                self.tem_imagem = true;
                gasto
            }
            None if self.reapresentar_agora() => {
                self.ritmo.contar_reapresentado();
                Duration::ZERO
            }
            None => return Ok(false),
        };
        let tempo = em_cem_nanos(self.comeco.elapsed());
        let marco = Instant::now();
        let codificacao = &self.codificacao;
        codificacao.compressor.entregar(&codificacao.amostra, tempo).map_err(em("entregar o quadro"))?;
        let entregue = Instant::now();
        self.ritmo.contar_trabalho(preparo + (entregue - marco));
        self.entregue_em = Some(entregue);
        self.chave_pendente = false;
        self.pedidos -= 1;
        self.quadro = self.quadro.wrapping_add(1);
        Ok(true)
    }

    fn reapresentar_agora(&self) -> bool {
        self.tem_imagem && (self.chave_pendente || self.ultima_saida.elapsed() >= SINAL_DE_VIDA)
    }

    fn preparar_o_quadro(&mut self) -> Result<Option<Duration>, String> {
        let bancada = &self.codificacao.bancada;
        match &mut self.fonte {
            Fonte::Sintetica => {
                let marco = Instant::now();
                bancada.pintar(self.quadro);
                bancada.preparar().map_err(em("preparar o quadro"))?;
                Ok(Some(marco.elapsed()))
            }
            Fonte::Janela(captura) => trazer_da_janela(captura, bancada),
        }
    }

    fn receber(&mut self) -> Result<(), String> {
        match self.codificacao.compressor.proximo_evento()? {
            DoCodificador::PedeEntrada => self.pedidos += 1,
            DoCodificador::Saida { bytes, chegou, custo, .. } => {
                let latencia = self.entregue_em.take().map(|entregue| chegou - entregue);
                self.ritmo.contar_trabalho(custo);
                self.enviar(&bytes, latencia);
            }
            DoCodificador::FimDoDreno | DoCodificador::Nenhum => {}
        }
        Ok(())
    }

    fn enviar(&mut self, bytes: &[u8], latencia: Option<Duration>) {
        let unidade = ler_unidade(bytes);
        self.anunciar_o_perfil(&unidade);
        self.saida.capture_encoded_frame(&EncodedVideoFrame {
            codec: EncodedVideoCodec::H264,
            payload: bytes,
            timestamp_us: 0,
            frame_type: if unidade.idr { EncodedFrameType::Key } else { EncodedFrameType::Delta },
            resolution: self.resolucao.clone(),
            frame_metadata: None,
        });
        self.ultima_saida = Instant::now();
        self.ritmo.contar_quadro(bytes.len(), unidade.idr, latencia);
    }

    fn anunciar_o_perfil(&mut self, unidade: &Unidade) {
        let (false, Some(perfil)) = (self.perfil_anunciado, unidade.perfil) else { return };
        self.perfil_anunciado = true;
        let hexa = format!("{:02x}{:02x}{:02x}", perfil.perfil, perfil.restricoes, perfil.nivel);
        self.escritor.manda(&Evento::transmissao("perfil", hexa));
    }
}

fn em_cem_nanos(tempo: Duration) -> i64 {
    (tempo.as_nanos() / 100) as i64
}

fn trazer_da_janela(captura: &mut CapturaDeJanela, bancada: &Bancada) -> Result<Option<Duration>, String> {
    let limite = Instant::now() + ESPERA_POR_QUADRO_NOVO;
    loop {
        let marco = Instant::now();
        match captura.colher().map_err(em("colher o quadro da janela"))? {
            Colheita::Quadro(quadro) => {
                bancada.trazer(&quadro.textura, captura.tamanho());
                return Ok(Some(marco.elapsed()));
            }
            Colheita::MudouDeTamanho(..) => {}
            Colheita::Nada => {
                let resta = limite.saturating_duration_since(Instant::now());
                if resta.is_zero() || !captura.esperar(resta) {
                    return Ok(None);
                }
            }
        }
    }
}

fn codificacao_na_primeira_placa(config: Configuracao, origem: Origem) -> Result<Codificacao, String> {
    let mut motivos = Vec::new();
    for placa in placas_de_hardware().map_err(em("listar as placas"))? {
        match abrir_codificacao(&placa, config, origem) {
            Ok(codificacao) => return Ok(codificacao),
            Err(motivo) => motivos.push(format!("{}: {motivo}", placa.nome)),
        }
    }
    Err(if motivos.is_empty() { "nenhuma placa de vídeo de hardware".into() } else { motivos.join("; ") })
}

fn como_subiu(pedido: &PedidoDeTela, aceitos: Aceitos, fonte: &Fonte) -> String {
    let origem = match fonte {
        Fonte::Sintetica => "fonte sintética".to_owned(),
        Fonte::Janela(captura) => {
            let (largura, altura) = captura.tamanho();
            format!("janela {largura}x{altura}")
        }
    };
    let mut texto = format!("{}x{} @{} · {origem}", pedido.largura, pedido.altura, pedido.fps);
    let ligados = [(aceitos.taxa_variavel, "taxa variável"), (aceitos.baixa_latencia, "baixa latência"), (aceitos.cabac, "CABAC")];
    for (_, nome) in ligados.into_iter().filter(|&(aceito, _)| aceito) {
        texto.push_str(" · ");
        texto.push_str(nome);
    }
    texto
}
