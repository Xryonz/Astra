mod conferencia_do_eco;
mod controles;
mod escolha_de_aparelho;
mod escuta;
mod estado_da_voz;
mod fala;
mod microfone;
mod queda;
mod repasse;
mod tela;

use crate::aparelhos::Sentido;
use crate::emissao::PedidoDeTela;
use crate::ponte::{Escritor, Evento};
use conferencia_do_eco::{conferir_o_eco, relatar_o_cancelador_do_windows};
use controles::{ALVO_DO_MICROFONE, Controles};
use escolha_de_aparelho::{Escolhas, levar_ao_aparelho_certo};
use escuta::aplicar_a_todos;
use livekit::prelude::{LocalAudioTrack, PlatformAudio, Room, RoomOptions};
use microfone::{abrir_ou_fechar, gravar_sem_o_cancelador_do_windows, publicar_o_microfone};
use repasse::{Repasse, repassar};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use tela::TelaNoAr;
use tokio::task::JoinHandle;

const OS_DOIS_SENTIDOS: [Sentido; 2] = [Sentido::Entrada, Sentido::Saida];

pub struct Sala {
    escritor: Arc<Escritor>,
    controles: Arc<Mutex<Controles>>,
    escolhas: Escolhas,
    aberta: Option<SalaAberta>,
}

struct SalaAberta {
    sala: Arc<Room>,
    microfone: LocalAudioTrack,
    audio: PlatformAudio,
    em_uso: Escolhas,
    tarefas: Vec<JoinHandle<()>>,
    tela: Option<TelaNoAr>,
}

impl Sala {
    pub fn nova(escritor: Arc<Escritor>) -> Self {
        Self { escritor, controles: Arc::default(), escolhas: Escolhas::default(), aberta: None }
    }

    pub async fn entrar(&mut self, url: &str, token: &str) -> Result<(), String> {
        self.deixar().await;
        let audio = PlatformAudio::new().map_err(|e| format!("abrir o áudio do sistema: {e}"))?;
        relatar_o_cancelador_do_windows(&audio);
        let (sala, eventos) = Room::connect(url, token, RoomOptions::default())
            .await
            .map_err(|e| format!("entrar na sala: {e}"))?;
        let mut em_uso = Escolhas::default();
        for sentido in OS_DOIS_SENTIDOS {
            levar_ao_aparelho_certo(&audio, &mut em_uso, sentido, self.escolhas.de(sentido)).await;
        }
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
        let sala = Arc::new(sala);
        let tarefas = vec![
            tokio::spawn(repassar(eventos, self.repasse(&sala))),
            tokio::spawn(conferir_o_eco(microfone.clone())),
        ];
        self.aberta = Some(SalaAberta { sala, microfone, audio, em_uso, tarefas, tela: None });
        self.contar_a_todos().await;
        Ok(())
    }

    pub async fn deixar(&mut self) {
        let Some(mut aberta) = self.aberta.take() else { return };
        if let Some(tela) = aberta.tela.take() {
            tela.tirar(&aberta.sala).await;
        }
        aberta.tarefas.iter().for_each(JoinHandle::abort);
        let _ = aberta.sala.close().await;
    }

    pub async fn transmitir(&mut self, pedido: PedidoDeTela) -> Result<(), String> {
        let escritor = Arc::clone(&self.escritor);
        let Some(aberta) = &mut self.aberta else { return Err("sem sala para transmitir".into()) };
        match &mut aberta.tela {
            Some(tela) => tela.trocar(pedido, escritor).await,
            None => {
                aberta.tela = Some(TelaNoAr::publicar(&aberta.sala, pedido, escritor).await?);
                Ok(())
            }
        }
    }

    pub async fn parar_a_tela(&mut self) {
        let Some(aberta) = &mut self.aberta else { return };
        let Some(tela) = aberta.tela.take() else { return };
        tela.tirar(&aberta.sala).await;
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

    pub async fn usar(&mut self, sentido: Sentido, id: String) {
        self.escolhas.definir(sentido, id);
        self.levar(sentido).await;
    }

    pub async fn reconciliar_aparelhos(&mut self) {
        for sentido in OS_DOIS_SENTIDOS {
            self.levar(sentido).await;
        }
    }

    async fn levar(&mut self, sentido: Sentido) {
        let Some(aberta) = &mut self.aberta else { return };
        levar_ao_aparelho_certo(&aberta.audio, &mut aberta.em_uso, sentido, self.escolhas.de(sentido)).await;
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

    fn repasse(&self, sala: &Arc<Room>) -> Repasse {
        Repasse::novo(Arc::clone(&self.escritor), Arc::clone(&self.controles), Arc::clone(sala))
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
