use super::controles::Controles;
use super::escuta::aplicar_a_faixa;
use super::estado_da_voz;
use super::queda::tipo_da_queda;
use crate::ponte::{Escritor, Evento};
use livekit::prelude::{LocalParticipant, RemoteParticipant, RoomEvent};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use tokio::sync::mpsc::UnboundedReceiver;

pub struct Repasse {
    pub escritor: Arc<Escritor>,
    pub controles: Arc<Mutex<Controles>>,
    pub local: LocalParticipant,
}

pub async fn repassar(mut eventos: UnboundedReceiver<RoomEvent>, repasse: Repasse) {
    while let Some(evento) = eventos.recv().await {
        registrar(&evento);
        repasse.agir(&evento).await;
        if let Some(traduzido) = traduzir(evento) {
            repasse.escritor.manda(&traduzido);
        }
    }
}

impl Repasse {
    async fn agir(&self, evento: &RoomEvent) {
        match evento {
            RoomEvent::ParticipantActive(quem) => self.contar_a(quem).await,
            RoomEvent::TrackSubscribed { track, participant, .. } => {
                let ouvir = self.controles().ouve(participant.identity().as_str());
                aplicar_a_faixa(track, ouvir);
            }
            RoomEvent::DataReceived { payload, participant: Some(quem), .. } => {
                estado_da_voz::repassar_o_que_chegou(&self.escritor, quem.identity().as_str(), payload);
            }
            _ => {}
        }
    }

    async fn contar_a(&self, quem: &RemoteParticipant) {
        let (mudo, surdo) = {
            let controles = self.controles();
            (controles.mudo(), controles.surdo())
        };
        estado_da_voz::contar(&self.local, mudo, surdo, vec![quem.identity()]).await;
    }

    fn controles(&self) -> MutexGuard<'_, Controles> {
        self.controles.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

fn registrar(evento: &RoomEvent) {
    match evento {
        RoomEvent::TrackSubscribed { participant, .. } => eprintln!("ouvindo {}", participant.identity()),
        RoomEvent::Reconnecting => eprintln!("reconectando"),
        RoomEvent::Reconnected => eprintln!("reconectado"),
        _ => {}
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
