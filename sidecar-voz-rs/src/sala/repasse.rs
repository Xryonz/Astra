use super::queda::tipo_da_queda;
use crate::ponte::{Escritor, Evento};
use livekit::prelude::RoomEvent;
use std::sync::Arc;
use tokio::sync::mpsc::UnboundedReceiver;

pub async fn repassar(mut eventos: UnboundedReceiver<RoomEvent>, escritor: Arc<Escritor>) {
    while let Some(evento) = eventos.recv().await {
        if let Some(traduzido) = traduzir(evento) {
            escritor.manda(&traduzido);
        }
    }
}

fn traduzir(evento: RoomEvent) -> Option<Evento> {
    match evento {
        RoomEvent::ParticipantConnected(quem) => Some(Evento::chegou(quem.identity().into())),
        RoomEvent::ParticipantDisconnected(quem) => Some(Evento::saiu(quem.identity().into())),
        RoomEvent::Disconnected { reason } => {
            Some(Evento::caiu_da_sala(tipo_da_queda(reason), reason.as_str_name()))
        }
        _ => None,
    }
}
