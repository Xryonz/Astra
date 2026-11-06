mod conferencia_do_eco;
mod controles;
mod escuta;
mod estado_da_voz;
mod microfone;
mod queda;
mod repasse;

use crate::ponte::{Escritor, Evento};
use conferencia_do_eco::{conferir_o_eco, relatar_o_cancelador_do_windows};
use controles::{ALVO_DO_MICROFONE, Controles};
use escuta::aplicar_a_todos;
use livekit::prelude::{LocalAudioTrack, PlatformAudio, Room, RoomOptions};
use microfone::{abrir_ou_fechar, gravar_sem_o_cancelador_do_windows, publicar_o_microfone};
use repasse::{Repasse, repassar};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use tokio::task::JoinHandle;

pub struct Sala {
    escritor: Arc<Escritor>,
    controles: Arc<Mutex<Controles>>,
    aberta: Option<SalaAberta>,
}

struct SalaAberta {
    sala: Room,
    microfone: LocalAudioTrack,
    _audio: PlatformAudio,
    tarefas: Vec<JoinHandle<()>>,
}

impl Sala {
    pub fn nova(escritor: Arc<Escritor>) -> Self {
        Self { escritor, controles: Arc::default(), aberta: None }
    }

    pub async fn entrar(&mut self, url: &str, token: &str) -> Result<(), String> {
        self.deixar().await;
        let audio = PlatformAudio::new().map_err(|e| format!("abrir o áudio do sistema: {e}"))?;
        relatar_o_cancelador_do_windows(&audio);
        let (sala, eventos) = Room::connect(url, token, RoomOptions::default())
            .await
            .map_err(|e| format!("entrar na sala: {e}"))?;
        gravar_sem_o_cancelador_do_windows(&audio);
        let aberto = self.controles().microfone_aberto();
        let microfone = match publicar_o_microfone(&sala, &audio, aberto).await {
            Ok(faixa) => faixa,
            Err(msg) => {
                let _ = sala.close().await;
                return Err(msg);
            }
        };
        eprintln!("na sala {:?} como {:?}", sala.name(), sala.local_participant().identity().to_string());
        self.anunciar_quem_esta(&sala);
        let tarefas = vec![
            tokio::spawn(repassar(eventos, self.repasse(&sala))),
            tokio::spawn(conferir_o_eco(microfone.clone())),
        ];
        self.aberta = Some(SalaAberta { sala, microfone, _audio: audio, tarefas });
        self.contar_a_todos().await;
        Ok(())
    }

    pub async fn deixar(&mut self) {
        let Some(aberta) = self.aberta.take() else { return };
        aberta.tarefas.iter().for_each(JoinHandle::abort);
        let _ = aberta.sala.close().await;
    }

    pub async fn mudar(&self, ligado: bool) {
        self.controles().definir_mudo(ligado);
        self.aplicar_ao_microfone();
        self.contar_a_todos().await;
    }

    pub async fn ensurdecer(&self, ligado: bool) {
        self.controles().definir_surdo(ligado);
        self.aplicar_a_quem_ouco();
        self.contar_a_todos().await;
    }

    pub fn ajustar_volume(&self, par: &str, volume: i32) {
        self.controles().definir_volume(par, volume);
        if par == ALVO_DO_MICROFONE {
            self.aplicar_ao_microfone();
        } else {
            self.aplicar_a_quem_ouco();
        }
    }

    fn aplicar_ao_microfone(&self) {
        let Some(aberta) = &self.aberta else { return };
        abrir_ou_fechar(&aberta.microfone, self.controles().microfone_aberto());
    }

    fn aplicar_a_quem_ouco(&self) {
        let Some(aberta) = &self.aberta else { return };
        aplicar_a_todos(&aberta.sala, &self.controles());
    }

    async fn contar_a_todos(&self) {
        let Some(aberta) = &self.aberta else { return };
        let (mudo, surdo) = {
            let controles = self.controles();
            (controles.mudo(), controles.surdo())
        };
        estado_da_voz::contar(&aberta.sala.local_participant(), mudo, surdo, Vec::new()).await;
    }

    fn repasse(&self, sala: &Room) -> Repasse {
        Repasse {
            escritor: Arc::clone(&self.escritor),
            controles: Arc::clone(&self.controles),
            local: sala.local_participant(),
        }
    }

    fn controles(&self) -> MutexGuard<'_, Controles> {
        self.controles.lock().unwrap_or_else(PoisonError::into_inner)
    }

    fn anunciar_quem_esta(&self, sala: &Room) {
        self.escritor.manda(&Evento::na_sala());
        for quem in sala.remote_participants().into_keys() {
            self.escritor.manda(&Evento::chegou(quem.into()));
        }
    }
}
