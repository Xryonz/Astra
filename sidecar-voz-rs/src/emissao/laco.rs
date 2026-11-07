use super::PedidoDeTela;
use super::ritmo::Ritmo;
use super::ritmo_que_cabe::RitmoQueCabe;
use super::troca::{Aberta, abrir_o_primeiro_que_aceitar, condenar};
use crate::com::ComDaThread;
use crate::entrega::Entrega;
use crate::ponte::{Escritor, Evento};
use crate::tela::{
    Bancada, Caminho, CapturaDeJanela, CapturaDoMonitor, Codificacao, Colheita, ColheitaDoMonitor, Configuracao,
    Entrada, Espelho, Evento as DoCodificador, Unidade, achar_o_monitor, em, ler_unidade, o_monitor_saiu,
};
use livekit::webrtc::video_frame::{EncodedFrameType, EncodedVideoCodec, EncodedVideoFrame};
use livekit::webrtc::video_source::VideoResolution;
use livekit::webrtc::video_source::native::NativeVideoSource;
use std::collections::HashSet;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use windows::Win32::Foundation::HWND;
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_FULL, MFShutdown, MFStartup};

const ESPERA_POR_QUADRO_NOVO: Duration = Duration::from_millis(100);
const SINAL_DE_VIDA: Duration = Duration::from_secs(2);

enum Fonte {
    Janela(CapturaDeJanela),
    Monitor(CapturaDoMonitor),
}

enum Falha {
    DoCodificador(String),
    Fatal(String),
}

fn do_codificador(etapa: &'static str) -> impl Fn(windows::core::Error) -> Falha {
    move |erro| Falha::DoCodificador(format!("{etapa}: {erro}"))
}

fn fatal(etapa: &'static str) -> impl Fn(windows::core::Error) -> Falha {
    move |erro| Falha::Fatal(format!("{etapa}: {erro}"))
}

pub fn transmitir(
    pedido: PedidoDeTela,
    fonte: NativeVideoSource,
    escritor: Arc<Escritor>,
    entrega: Option<Entrega>,
    parar: Arc<AtomicBool>,
) {
    let _com = ComDaThread::iniciar();
    if let Err(msg) = transmitir_com_o_media_foundation(pedido, fonte, &escritor, entrega, parar) {
        escritor.manda(&Evento::erro(format!("transmissão parou: {msg}")));
    }
    escritor.manda(&Evento::transmissao_encerrada());
}

fn transmitir_com_o_media_foundation(
    pedido: PedidoDeTela,
    fonte: NativeVideoSource,
    escritor: &Arc<Escritor>,
    entrega: Option<Entrega>,
    parar: Arc<AtomicBool>,
) -> Result<(), String> {
    unsafe { MFStartup(MF_VERSION, MFSTARTUP_FULL) }.map_err(em("iniciar o Media Foundation"))?;
    let feito = Laco::novo(pedido, fonte, Arc::clone(escritor), entrega, parar).rodar();
    let _ = unsafe { MFShutdown() };
    feito
}

struct Laco {
    pedido: PedidoDeTela,
    saida: NativeVideoSource,
    escritor: Arc<Escritor>,
    entrega: Option<Entrega>,
    parar: Arc<AtomicBool>,
    comeco: Instant,
    fps: u32,
    intervalo: Duration,
    cabe: RitmoQueCabe,
    kbps: u32,
    ultima_saida: Instant,
    vez: Instant,
    ritmo: Ritmo,
    condenados: HashSet<String>,
}

struct Turno {
    fonte: Fonte,
    codificacao: Codificacao,
    identidade: String,
    resolucao: VideoResolution,
    pedidos: u32,
    entregue_em: Option<Instant>,
    quadros: u32,
    chave_pendente: bool,
    tem_imagem: bool,
    perfil_anunciado: bool,
    espelho: Option<Espelho>,
}

impl Laco {
    fn novo(
        pedido: PedidoDeTela,
        saida: NativeVideoSource,
        escritor: Arc<Escritor>,
        entrega: Option<Entrega>,
        parar: Arc<AtomicBool>,
    ) -> Self {
        let agora = Instant::now();
        Self {
            pedido,
            saida,
            escritor,
            entrega,
            parar,
            comeco: agora,
            fps: pedido.fps,
            intervalo: Duration::from_secs(1) / pedido.fps,
            cabe: RitmoQueCabe::novo(pedido.fps),
            kbps: pedido.kbps,
            ultima_saida: agora,
            vez: agora,
            ritmo: Ritmo::novo(),
            condenados: HashSet::new(),
        }
    }

    fn rodar(&mut self) -> Result<(), String> {
        loop {
            let mut turno = self.abrir_turno()?;
            match self.rodar_o_turno(&mut turno) {
                Ok(()) => return Ok(()),
                Err(Falha::Fatal(motivo)) => return Err(motivo),
                Err(Falha::DoCodificador(motivo)) => self.trocar_de_codificador(&turno, &motivo),
            }
        }
    }

    fn abrir_turno(&mut self) -> Result<Turno, String> {
        let config = Configuracao {
            largura: self.pedido.largura,
            altura: self.pedido.altura,
            fps: self.fps,
            kbps: self.pedido.kbps,
            entrada: Entrada::Argb,
        };
        let (aberta, fonte) = self.abrir_com_a_fonte(config)?;
        let (width, height) = aberta.codificacao.saida;
        let espelho = self.entrega.is_some().then(|| Espelho::novo(aberta.codificacao.dispositivo()));
        let mut turno = Turno {
            fonte,
            codificacao: aberta.codificacao,
            identidade: aberta.identidade,
            resolucao: VideoResolution { width, height },
            pedidos: 0,
            entregue_em: None,
            quadros: 0,
            chave_pendente: false,
            tem_imagem: false,
            perfil_anunciado: false,
            espelho,
        };
        if self.kbps != self.pedido.kbps && !turno.codificacao.compressor.mudar_taxa(self.kbps) {
            self.kbps = self.pedido.kbps;
        }
        let resumo = como_subiu(self.fps, &turno.codificacao, &turno.fonte);
        self.escritor.manda(&Evento::transmissao(&turno.codificacao.nome, resumo));
        Ok(turno)
    }

    fn abrir_com_a_fonte(&self, config: Configuracao) -> Result<(Aberta, Fonte), String> {
        if let Some(janela) = self.pedido.janela {
            let aberta = abrir_o_primeiro_que_aceitar(config, None, &self.condenados)?;
            let captura = CapturaDeJanela::abrir(aberta.codificacao.placa(), HWND(janela as *mut _))
                .map_err(em("abrir a captura da janela"))?;
            return Ok((aberta, Fonte::Janela(captura)));
        }
        let (placa, saida) = achar_o_monitor(self.pedido.monitor)?;
        let aberta = abrir_o_primeiro_que_aceitar(config, Some(&placa), &self.condenados)?;
        let captura = CapturaDoMonitor::abrir(aberta.codificacao.placa(), saida).map_err(em("duplicar o monitor"))?;
        Ok((aberta, Fonte::Monitor(captura)))
    }

    fn trocar_de_codificador(&mut self, turno: &Turno, motivo: &str) {
        let (nome, quadros) = (&turno.codificacao.nome, turno.quadros);
        let recado = if condenar(&mut self.condenados, &turno.identidade, quadros) {
            format!("{nome} falhou no quadro {quadros} ({motivo}); trocando de compressor")
        } else {
            format!("{nome} falhou depois de {quadros} quadros ({motivo}); reabrindo o mesmo")
        };
        self.escritor.manda(&Evento::transmissao("compressor", recado));
    }

    fn rodar_o_turno(&mut self, turno: &mut Turno) -> Result<(), Falha> {
        while !self.parar.load(Ordering::Relaxed) {
            self.fechar_o_segundo(turno);
            if turno.entregue_em.is_none() && turno.pedidos > 0 {
                self.esperar_a_vez();
                self.atender_a_rede(turno);
                if !self.alimentar(turno)? {
                    continue;
                }
            }
            self.receber(turno)?;
        }
        Ok(())
    }

    fn fechar_o_segundo(&mut self, turno: &mut Turno) {
        let Some(segundo) = self.ritmo.fechar_o_segundo(self.kbps) else { return };
        self.escritor.manda(&Evento::transmissao("ritmo", segundo.resumo));
        if let Some(trabalho) = segundo.trabalho_por_quadro {
            self.seguir_a_maquina(trabalho, turno);
        }
    }

    fn seguir_a_maquina(&mut self, trabalho: Duration, turno: &mut Turno) {
        let Some(fps) = self.cabe.segundo(trabalho, self.fps) else { return };
        let rumo = if fps > self.fps { "subindo" } else { "caindo" };
        self.fps = fps;
        self.intervalo = Duration::from_secs(1) / fps;
        turno.codificacao.compressor.ajustar_quadros(fps);
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

    fn atender_a_rede(&mut self, turno: &mut Turno) {
        if self.saida.take_keyframe_request() {
            self.ritmo.contar_pedido_de_chave();
            turno.codificacao.compressor.pedir_quadro_chave();
            turno.chave_pendente = true;
        }
        if let Some(pedido) = self.saida.take_rate_control_request() {
            self.seguir_a_rede(pedido.target_bitrate_bps, turno);
        }
    }

    fn seguir_a_rede(&mut self, bps: u64, turno: &mut Turno) {
        if bps == 0 {
            return;
        }
        let kbps = (bps / 1000).clamp(1, u64::from(self.pedido.kbps)) as u32;
        self.ritmo.anotar_alvo_da_rede(kbps);
        if kbps != self.kbps && turno.codificacao.compressor.mudar_taxa(kbps) {
            self.kbps = kbps;
        }
    }

    fn alimentar(&mut self, turno: &mut Turno) -> Result<bool, Falha> {
        let (preparo, novo) = match self.preparar_o_quadro(turno)? {
            Some(gasto) => {
                turno.tem_imagem = true;
                (gasto, true)
            }
            None if self.reapresentar_agora(turno) => {
                self.ritmo.contar_reapresentado();
                (Duration::ZERO, false)
            }
            None => return Ok(false),
        };
        let tempo = em_cem_nanos(self.comeco.elapsed());
        let marco = Instant::now();
        let codificacao = &mut turno.codificacao;
        codificacao.compressor.entregar(&codificacao.amostra, tempo).map_err(do_codificador("entregar o quadro"))?;
        let entregue = Instant::now();
        self.ritmo.contar_trabalho(preparo + (entregue - marco));
        turno.entregue_em = Some(entregue);
        turno.chave_pendente = false;
        turno.pedidos -= 1;
        turno.quadros += 1;
        if novo {
            self.espelhar(turno);
        }
        Ok(true)
    }

    fn espelhar(&self, turno: &mut Turno) {
        let (Some(entrega), Some(espelho)) = (&self.entrega, turno.espelho.as_mut()) else { return };
        let (fonte, tamanho) = turno.codificacao.bancada.fonte_do_espelho();
        match espelho.talvez(fonte, tamanho) {
            Ok(Some(quadro)) => entrega.mandar("", quadro),
            Ok(None) => {}
            Err(erro) => {
                eprintln!("espelho desligado: {erro}");
                turno.espelho = None;
            }
        }
    }

    fn reapresentar_agora(&self, turno: &Turno) -> bool {
        turno.tem_imagem && (turno.chave_pendente || self.ultima_saida.elapsed() >= SINAL_DE_VIDA)
    }

    fn preparar_o_quadro(&mut self, turno: &mut Turno) -> Result<Option<Duration>, Falha> {
        let bancada = &mut turno.codificacao.bancada;
        match &mut turno.fonte {
            Fonte::Janela(captura) => trazer_da_janela(captura, bancada, &mut self.ritmo),
            Fonte::Monitor(captura) => trazer_do_monitor(captura, bancada),
        }
    }

    fn receber(&mut self, turno: &mut Turno) -> Result<(), Falha> {
        match turno.codificacao.compressor.proximo_evento().map_err(Falha::DoCodificador)? {
            DoCodificador::PedeEntrada => turno.pedidos += 1,
            DoCodificador::Engoliu => {
                turno.entregue_em = None;
                turno.pedidos += 1;
            }
            DoCodificador::Saida { bytes, chegou, custo, .. } => {
                let latencia = turno.entregue_em.take().map(|entregue| chegou - entregue);
                self.ritmo.contar_trabalho(custo);
                self.enviar(turno, &bytes, latencia);
            }
            DoCodificador::FimDoDreno | DoCodificador::Nenhum => {}
        }
        Ok(())
    }

    fn enviar(&mut self, turno: &mut Turno, bytes: &[u8], latencia: Option<Duration>) {
        let unidade = ler_unidade(bytes);
        self.anunciar_o_perfil(turno, &unidade);
        self.saida.capture_encoded_frame(&EncodedVideoFrame {
            codec: EncodedVideoCodec::H264,
            payload: bytes,
            timestamp_us: 0,
            frame_type: if unidade.idr { EncodedFrameType::Key } else { EncodedFrameType::Delta },
            resolution: turno.resolucao.clone(),
            frame_metadata: None,
        });
        self.ultima_saida = Instant::now();
        self.ritmo.contar_quadro(bytes.len(), unidade.idr, latencia);
    }

    fn anunciar_o_perfil(&self, turno: &mut Turno, unidade: &Unidade) {
        let (false, Some(perfil)) = (turno.perfil_anunciado, unidade.perfil) else { return };
        turno.perfil_anunciado = true;
        let hexa = format!("{:02x}{:02x}{:02x}", perfil.perfil, perfil.restricoes, perfil.nivel);
        self.escritor.manda(&Evento::transmissao("perfil", hexa));
    }
}

fn em_cem_nanos(tempo: Duration) -> i64 {
    (tempo.as_nanos() / 100) as i64
}

fn trazer_da_janela(
    captura: &mut CapturaDeJanela,
    bancada: &mut Bancada,
    ritmo: &mut Ritmo,
) -> Result<Option<Duration>, Falha> {
    let limite = Instant::now() + ESPERA_POR_QUADRO_NOVO;
    loop {
        let marco = Instant::now();
        match captura.colher().map_err(fatal("colher o quadro da janela"))? {
            Colheita::Quadro(quadro) => {
                bancada
                    .trazer(&quadro.textura, captura.tamanho())
                    .map_err(do_codificador("encaixar o quadro da janela"))?;
                return Ok(Some(marco.elapsed()));
            }
            Colheita::MudouDeTamanho(largura, altura) => ritmo.anotar_janela(largura, altura),
            Colheita::Nada => {
                let resta = limite.saturating_duration_since(Instant::now());
                if resta.is_zero() || !captura.esperar(resta) {
                    return Ok(None);
                }
            }
        }
    }
}

fn trazer_do_monitor(captura: &mut CapturaDoMonitor, bancada: &mut Bancada) -> Result<Option<Duration>, Falha> {
    if captura.perdida() && !recuperar_o_monitor(captura)? {
        return Ok(None);
    }
    match captura.colher(ESPERA_POR_QUADRO_NOVO, true).map_err(fatal("colher o quadro do monitor"))? {
        ColheitaDoMonitor::Quadro(quadro) => {
            let marco = Instant::now();
            bancada
                .trazer(&quadro.textura, captura.tamanho())
                .map_err(do_codificador("encaixar o quadro do monitor"))?;
            Ok(Some(marco.elapsed()))
        }
        ColheitaDoMonitor::Nada => Ok(None),
        ColheitaDoMonitor::Perdido => {
            eprintln!("acesso ao monitor perdido; a última imagem segue no ar");
            Ok(None)
        }
    }
}

fn recuperar_o_monitor(captura: &mut CapturaDoMonitor) -> Result<bool, Falha> {
    match captura.remontar() {
        Ok(()) => {
            let (largura, altura) = captura.tamanho();
            eprintln!("monitor de volta em {largura}x{altura}");
            Ok(true)
        }
        Err(erro) if o_monitor_saiu(&erro) => Err(Falha::Fatal(format!("o monitor saiu: {erro}"))),
        Err(_) => {
            std::thread::sleep(ESPERA_POR_QUADRO_NOVO);
            Ok(false)
        }
    }
}

fn como_subiu(fps: u32, codificacao: &Codificacao, fonte: &Fonte) -> String {
    let origem = match fonte {
        Fonte::Janela(captura) => {
            let (largura, altura) = captura.tamanho();
            format!("janela {largura}x{altura}")
        }
        Fonte::Monitor(captura) => {
            let (largura, altura) = captura.tamanho();
            format!("monitor {largura}x{altura}")
        }
    };
    let (largura, altura) = codificacao.saida;
    let na_memoria = codificacao.caminho == Caminho::Memoria;
    let sem_placa = if na_memoria { "sem aceleração de placa · " } else { "" };
    let taxa = if na_memoria { "taxa constante" } else { "taxa variável" };
    let mut texto = format!("{sem_placa}{largura}x{altura} @{fps} · {origem}");
    let aceitos = codificacao.compressor.aceitos;
    let ligados = [(aceitos.controle_de_taxa, taxa), (aceitos.baixa_latencia, "baixa latência"), (aceitos.cabac, "CABAC")];
    for (_, nome) in ligados.into_iter().filter(|&(aceito, _)| aceito) {
        texto.push_str(" · ");
        texto.push_str(nome);
    }
    texto
}
