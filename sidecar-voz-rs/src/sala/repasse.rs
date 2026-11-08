use super::controles::Controles;
use super::escuta::aplicar_a_faixa;
use super::estado_da_voz;
use super::fala::QuemFala;
use super::palco::{e_tela, telas_de};
use super::queda::tipo_da_queda;
use crate::ponte::{Escritor, Evento};
use livekit::prelude::{RemoteParticipant, RemoteTrack, RemoteTrackPublication, Room, RoomEvent};
use std::collections::HashSet;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use tokio::sync::mpsc::UnboundedReceiver;

pub struct Repasse {
    escritor: Arc<Escritor>,
    controles: Arc<Mutex<Controles>>,
    sala: Arc<Room>,
    quem_fala: QuemFala,
    com_tela: HashSet<String>,
}

pub async fn repassar(mut eventos: UnboundedReceiver<RoomEvent>, mut repasse: Repasse) {
    repasse.anunciar_as_telas_presentes();
    while let Some(evento) = eventos.recv().await {
        registrar(&evento);
        repasse.agir(&evento).await;
        if let Some(traduzido) = traduzir(evento) {
            repasse.escritor.manda(&traduzido);
        }
    }
}

impl Repasse {
    pub fn novo(escritor: Arc<Escritor>, controles: Arc<Mutex<Controles>>, sala: Arc<Room>) -> Self {
        Self { escritor, controles, sala, quem_fala: QuemFala::default(), com_tela: HashSet::new() }
    }

    async fn agir(&mut self, evento: &RoomEvent) {
        match evento {
            RoomEvent::ActiveSpeakersChanged { .. } => self.quem_fala.atualizar(&self.sala, &self.escritor),
            RoomEvent::ParticipantActive(quem) => self.contar_a(quem).await,
            RoomEvent::TrackSubscribed { track, participant, .. } => {
                let ouvir = self.controles().ouve(participant.identity().as_str());
                aplicar_a_faixa(track, ouvir);
            }
            RoomEvent::DataReceived { payload, participant: Some(quem), .. } => {
                estado_da_voz::repassar_o_que_chegou(&self.escritor, quem.identity().as_str(), payload);
            }
            RoomEvent::TrackPublished { publication, participant } if e_tela(publication) => {
                self.chegou_uma_tela(publication, participant.identity().as_str());
            }
            RoomEvent::TrackUnpublished { publication, participant } if e_tela(publication) => {
                self.saiu_a_tela(participant.identity().as_str());
            }
            RoomEvent::ParticipantDisconnected(quem) => self.saiu_a_tela(quem.identity().as_str()),
            _ => {}
        }
    }

    fn anunciar_as_telas_presentes(&mut self) {
        for (quem, participante) in self.sala.remote_participants() {
            for tela in telas_de(&participante) {
                self.chegou_uma_tela(&tela, quem.as_str());
            }
        }
    }

    fn chegou_uma_tela(&mut self, tela: &RemoteTrackPublication, quem: &str) {
        tela.set_subscribed(self.controles().assiste_a(quem));
        if self.com_tela.insert(quem.to_owned()) {
            self.escritor.manda(&Evento::tela_no_ar(quem));
        }
    }

    fn saiu_a_tela(&mut self, quem: &str) {
        if self.com_tela.remove(quem) {
            self.escritor.manda(&Evento::tela_fora_do_ar(quem));
        }
    }

    async fn contar_a(&self, quem: &RemoteParticipant) {
        let (mudo, surdo) = {
            let controles = self.controles();
            (controles.mudo(), controles.surdo())
        };
        estado_da_voz::contar(&self.sala.local_participant(), mudo, surdo, vec![quem.identity()]).await;
    }

    fn controles(&self) -> MutexGuard<'_, Controles> {
        self.controles.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

fn registrar(evento: &RoomEvent) {
    match evento {
        RoomEvent::TrackSubscribed { track: RemoteTrack::Video(_), participant, .. } => {
            eprintln!("vendo a tela de {}", participant.identity());
        }
        RoomEvent::TrackUnsubscribed { track: RemoteTrack::Video(_), participant, .. } => {
            eprintln!("deixou de ver a tela de {}", participant.identity());
        }
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
