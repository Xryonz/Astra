use crate::emissao::{Emissor, PedidoDeTela};
use crate::ponte::Escritor;
use livekit::options::{TrackPublishOptions, VideoCodec, VideoEncoding};
use livekit::prelude::{LocalTrack, LocalVideoTrack, Room, TrackSid, TrackSource};
use livekit::webrtc::rtp_sender::VideoEncoderBackend;
use livekit::webrtc::video_source::native::NativeVideoSource;
use livekit::webrtc::video_source::{RtcVideoSource, VideoResolution};
use std::sync::Arc;

const NOME_DA_TELA: &str = "astra-tela";

pub struct TelaNoAr {
    publicacao: TrackSid,
    fonte: NativeVideoSource,
    emissor: Emissor,
}

impl TelaNoAr {
    pub async fn publicar(sala: &Room, pedido: PedidoDeTela, escritor: Arc<Escritor>) -> Result<Self, String> {
        let fonte = NativeVideoSource::new_encoded(VideoResolution { width: pedido.largura, height: pedido.altura });
        let mut emissor = Emissor::ligar(pedido, fonte.clone(), escritor)?;
        let faixa = LocalVideoTrack::create_video_track(NOME_DA_TELA, RtcVideoSource::Native(fonte.clone()));
        match sala.local_participant().publish_track(LocalTrack::Video(faixa), opcoes_da_tela(pedido)).await {
            Ok(publicacao) => Ok(Self { publicacao: publicacao.sid(), fonte, emissor }),
            Err(e) => {
                emissor.parar().await;
                Err(format!("publicar a tela: {e}"))
            }
        }
    }

    pub async fn trocar(&mut self, pedido: PedidoDeTela, escritor: Arc<Escritor>) -> Result<(), String> {
        self.emissor.parar().await;
        self.emissor = Emissor::ligar(pedido, self.fonte.clone(), escritor)?;
        Ok(())
    }

    pub async fn tirar(mut self, sala: &Room) {
        self.emissor.parar().await;
        if let Err(e) = sala.local_participant().unpublish_track(&self.publicacao).await {
            eprintln!("despublicar a tela: {e}");
        }
    }
}

fn opcoes_da_tela(pedido: PedidoDeTela) -> TrackPublishOptions {
    TrackPublishOptions {
        source: TrackSource::Screenshare,
        video_codec: VideoCodec::H264,
        video_encoder: VideoEncoderBackend::PreEncoded,
        simulcast: false,
        video_encoding: Some(VideoEncoding {
            max_bitrate: u64::from(pedido.kbps) * 1000,
            max_framerate: f64::from(pedido.fps),
        }),
        ..Default::default()
    }
}
