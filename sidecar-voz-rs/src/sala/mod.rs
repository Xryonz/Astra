mod conferencia_do_eco;
mod microfone;
mod queda;
mod repasse;

use crate::ponte::{Escritor, Evento};
use conferencia_do_eco::{conferir_o_eco, relatar_o_cancelador_do_windows};
use livekit::prelude::{PlatformAudio, Room, RoomOptions};
use microfone::publicar_o_microfone;
use repasse::repassar;
use std::sync::Arc;
use tokio::task::JoinHandle;

pub struct Sala {
    escritor: Arc<Escritor>,
    aberta: Option<SalaAberta>,
}

struct SalaAberta {
    sala: Room,
    _audio: PlatformAudio,
    tarefas: Vec<JoinHandle<()>>,
}

impl Sala {
    pub fn nova(escritor: Arc<Escritor>) -> Self {
        Self { escritor, aberta: None }
    }

    pub async fn entrar(&mut self, url: &str, token: &str) -> Result<(), String> {
        self.deixar().await;
        let audio = PlatformAudio::new().map_err(|e| format!("abrir o áudio do sistema: {e}"))?;
        relatar_o_cancelador_do_windows(&audio);
        let (sala, eventos) = Room::connect(url, token, RoomOptions::default())
            .await
            .map_err(|e| format!("entrar na sala: {e}"))?;
        let microfone = match publicar_o_microfone(&sala, &audio).await {
            Ok(faixa) => faixa,
            Err(msg) => {
                let _ = sala.close().await;
                return Err(msg);
            }
        };
        eprintln!("na sala {:?} como {:?}", sala.name(), sala.local_participant().identity().to_string());
        self.anunciar_quem_esta(&sala);
        let tarefas = vec![
            tokio::spawn(repassar(eventos, Arc::clone(&self.escritor))),
            tokio::spawn(conferir_o_eco(microfone)),
        ];
        self.aberta = Some(SalaAberta { sala, _audio: audio, tarefas });
        Ok(())
    }

    pub async fn deixar(&mut self) {
        let Some(aberta) = self.aberta.take() else { return };
        aberta.tarefas.iter().for_each(JoinHandle::abort);
        let _ = aberta.sala.close().await;
    }

    fn anunciar_quem_esta(&self, sala: &Room) {
        self.escritor.manda(&Evento::na_sala());
        for quem in sala.remote_participants().into_keys() {
            self.escritor.manda(&Evento::chegou(quem.into()));
        }
    }
}
