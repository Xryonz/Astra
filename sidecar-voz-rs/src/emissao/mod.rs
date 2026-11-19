mod laco;
mod ritmo;
mod ritmo_que_cabe;
mod troca;

use crate::entrega::Entrega;
use crate::ponte::Escritor;
use livekit::webrtc::video_source::native::NativeVideoSource;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::JoinHandle;

#[derive(Clone, Copy)]
pub struct PedidoDeTela {
    pub largura: u32,
    pub altura: u32,
    pub fps: u32,
    pub kbps: u32,
    pub janela: Option<u64>,
    pub monitor: u32,
}

pub struct Emissor {
    parar: Arc<AtomicBool>,
    linha: Option<JoinHandle<()>>,
}

impl Emissor {
    pub fn ligar(
        pedido: PedidoDeTela,
        fonte: NativeVideoSource,
        escritor: Arc<Escritor>,
        entrega: Option<Entrega>,
    ) -> Result<Self, String> {
        let parar = Arc::new(AtomicBool::new(false));
        let sinal = Arc::clone(&parar);
        let linha = std::thread::Builder::new()
            .name("astra-tela".into())
            .spawn(move || laco::transmitir(pedido, fonte, escritor, entrega, sinal))
            .map_err(|e| format!("abrir a linha da transmissão: {e}"))?;
        Ok(Self { parar, linha: Some(linha) })
    }

    pub async fn parar(&mut self) {
        self.parar.store(true, Ordering::Relaxed);
        let Some(linha) = self.linha.take() else { return };
        let _ = tokio::task::spawn_blocking(move || linha.join()).await;
    }
}

impl Drop for Emissor {
    fn drop(&mut self) {
        self.parar.store(true, Ordering::Relaxed);
    }
}
