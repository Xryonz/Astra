mod nv12;
mod relatorio;

use crate::entrega::Entrega;
use crate::ponte::{Escritor, Evento};
use futures_core::Stream;
use livekit::prelude::RemoteVideoTrack;
use livekit::webrtc::video_frame::BoxVideoFrame;
use livekit::webrtc::video_stream::native::NativeVideoStream;
use nv12::nv12_do_quadro;
use relatorio::Relatorio;
use std::future::poll_fn;
use std::pin::Pin;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::task::JoinHandle;

const SILENCIO_QUE_AVISA: Duration = Duration::from_secs(5);
const NOME_DO_DECODIFICADOR: &str = "decodificador do libwebrtc";

pub struct Recepcao {
    tarefa: JoinHandle<()>,
}

impl Drop for Recepcao {
    fn drop(&mut self) {
        self.tarefa.abort();
    }
}

pub fn receber_a_tela(par: &str, faixa: &RemoteVideoTrack, escritor: Arc<Escritor>, entrega: Option<Entrega>) -> Recepcao {
    let fluxo = NativeVideoStream::new(faixa.rtc_track());
    Recepcao { tarefa: tokio::spawn(receber(par.to_owned(), fluxo, escritor, entrega)) }
}

async fn receber(par: String, mut fluxo: NativeVideoStream, escritor: Arc<Escritor>, entrega: Option<Entrega>) {
    escritor.manda(&Evento::tela_com_decodificador(&par, NOME_DO_DECODIFICADOR));
    let mut relatorio = Relatorio::novo(Instant::now());
    let mut parada = false;
    loop {
        match tokio::time::timeout(SILENCIO_QUE_AVISA, proximo(&mut fluxo)).await {
            Ok(Some(quadro)) => {
                parada = false;
                let marco = Instant::now();
                let tamanho = entregar(&par, &quadro, entrega.as_ref());
                relatorio.contar(tamanho, marco.elapsed());
                if let Some(msg) = relatorio.fechar_o_segundo(Instant::now()) {
                    escritor.manda(&Evento::ritmo_da_tela(&par, msg));
                }
            }
            Ok(None) => return,
            Err(_) if !parada => {
                parada = true;
                eprintln!("a tela de {par} está sem quadros há {} s", SILENCIO_QUE_AVISA.as_secs());
            }
            Err(_) => {}
        }
    }
}

async fn proximo(fluxo: &mut NativeVideoStream) -> Option<BoxVideoFrame> {
    poll_fn(|contexto| Pin::new(&mut *fluxo).poll_next(contexto)).await
}

fn entregar(par: &str, quadro: &BoxVideoFrame, entrega: Option<&Entrega>) -> (u32, u32) {
    let buffer = quadro.buffer.as_ref();
    if let Some(entrega) = entrega
        && let Some(nv12) = nv12_do_quadro(buffer, |tamanho| entrega.reservar(tamanho))
    {
        entrega.mandar(par, nv12);
    }
    (buffer.width(), buffer.height())
}
