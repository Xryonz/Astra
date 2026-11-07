use super::PedidoDeTela;
use super::ritmo::Ritmo;
use crate::com::ComDaThread;
use crate::ponte::{Escritor, Evento};
use crate::tela::{
    Aceitos, Codificacao, Configuracao, Entrada, Evento as DoCodificador, Unidade, abrir_codificacao, em,
    ler_unidade, placas_de_hardware,
};
use livekit::webrtc::video_frame::{EncodedFrameType, EncodedVideoCodec, EncodedVideoFrame};
use livekit::webrtc::video_source::VideoResolution;
use livekit::webrtc::video_source::native::NativeVideoSource;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_FULL, MFShutdown, MFStartup};

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
    codificacao: Codificacao,
    fonte: NativeVideoSource,
    escritor: Arc<Escritor>,
    parar: Arc<AtomicBool>,
    resolucao: VideoResolution,
    intervalo: Duration,
    teto_kbps: u32,
    kbps: u32,
    pedidos: u32,
    quadro: u32,
    entregue_em: Option<Instant>,
    vez: Instant,
    perfil_anunciado: bool,
    ritmo: Ritmo,
}

impl Laco {
    fn abrir(
        pedido: PedidoDeTela,
        fonte: NativeVideoSource,
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
        let codificacao = codificacao_na_primeira_placa(config)?;
        escritor.manda(&Evento::transmissao(&codificacao.nome, como_subiu(&pedido, codificacao.compressor.aceitos)));
        Ok(Self {
            codificacao,
            fonte,
            escritor,
            parar,
            resolucao: VideoResolution { width: pedido.largura, height: pedido.altura },
            intervalo: Duration::from_secs(1) / pedido.fps,
            teto_kbps: pedido.kbps,
            kbps: pedido.kbps,
            pedidos: 0,
            quadro: 0,
            entregue_em: None,
            vez: Instant::now(),
            perfil_anunciado: false,
            ritmo: Ritmo::novo(),
        })
    }

    fn rodar(&mut self) -> Result<(), String> {
        while !self.parar.load(Ordering::Relaxed) {
            if self.entregue_em.is_none() && self.pedidos > 0 {
                self.esperar_a_vez();
                self.atender_a_rede();
                self.alimentar()?;
            }
            self.receber()?;
            if let Some(resumo) = self.ritmo.fechar_o_segundo(self.kbps) {
                self.escritor.manda(&Evento::transmissao("ritmo", resumo));
            }
        }
        Ok(())
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
        if self.fonte.take_keyframe_request() {
            self.ritmo.contar_pedido_de_chave();
            self.codificacao.compressor.pedir_quadro_chave();
        }
        if let Some(pedido) = self.fonte.take_rate_control_request() {
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

    fn alimentar(&mut self) -> Result<(), String> {
        let codificacao = &self.codificacao;
        codificacao.bancada.pintar(self.quadro);
        codificacao.bancada.preparar().map_err(em("preparar o quadro"))?;
        codificacao.compressor.entregar(&codificacao.amostra, self.quadro).map_err(em("entregar o quadro"))?;
        self.entregue_em = Some(Instant::now());
        self.pedidos -= 1;
        self.quadro = self.quadro.wrapping_add(1);
        Ok(())
    }

    fn receber(&mut self) -> Result<(), String> {
        match self.codificacao.compressor.proximo_evento()? {
            DoCodificador::PedeEntrada => self.pedidos += 1,
            DoCodificador::Saida { bytes, chegou, .. } => {
                let latencia = self.entregue_em.take().map(|entregue| chegou - entregue);
                self.enviar(&bytes, latencia);
            }
            DoCodificador::FimDoDreno | DoCodificador::Nenhum => {}
        }
        Ok(())
    }

    fn enviar(&mut self, bytes: &[u8], latencia: Option<Duration>) {
        let unidade = ler_unidade(bytes);
        self.anunciar_o_perfil(&unidade);
        self.fonte.capture_encoded_frame(&EncodedVideoFrame {
            codec: EncodedVideoCodec::H264,
            payload: bytes,
            timestamp_us: 0,
            frame_type: if unidade.idr { EncodedFrameType::Key } else { EncodedFrameType::Delta },
            resolution: self.resolucao.clone(),
            frame_metadata: None,
        });
        self.ritmo.contar_quadro(bytes.len(), unidade.idr, latencia);
    }

    fn anunciar_o_perfil(&mut self, unidade: &Unidade) {
        let (false, Some(perfil)) = (self.perfil_anunciado, unidade.perfil) else { return };
        self.perfil_anunciado = true;
        let hexa = format!("{:02x}{:02x}{:02x}", perfil.perfil, perfil.restricoes, perfil.nivel);
        self.escritor.manda(&Evento::transmissao("perfil", hexa));
    }
}

fn codificacao_na_primeira_placa(config: Configuracao) -> Result<Codificacao, String> {
    let mut motivos = Vec::new();
    for placa in placas_de_hardware().map_err(em("listar as placas"))? {
        match abrir_codificacao(&placa, config) {
            Ok(codificacao) => return Ok(codificacao),
            Err(motivo) => motivos.push(format!("{}: {motivo}", placa.nome)),
        }
    }
    Err(if motivos.is_empty() { "nenhuma placa de vídeo de hardware".into() } else { motivos.join("; ") })
}

fn como_subiu(pedido: &PedidoDeTela, aceitos: Aceitos) -> String {
    let mut texto = format!("{}x{} @{} · fonte sintética", pedido.largura, pedido.altura, pedido.fps);
    let ligados = [(aceitos.taxa_variavel, "taxa variável"), (aceitos.baixa_latencia, "baixa latência"), (aceitos.cabac, "CABAC")];
    for (_, nome) in ligados.into_iter().filter(|&(aceito, _)| aceito) {
        texto.push_str(" · ");
        texto.push_str(nome);
    }
    texto
}
