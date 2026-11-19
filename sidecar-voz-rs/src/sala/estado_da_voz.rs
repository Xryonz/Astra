use crate::ponte::{Escritor, Evento};
use livekit::prelude::{DataPacket, LocalParticipant, ParticipantIdentity};
use serde::{Deserialize, Serialize};

const RECADO_DO_ESTADO: &str = "estado-da-voz";

#[derive(Debug, PartialEq, Serialize, Deserialize)]
struct Recado {
    astra: String,
    mudo: bool,
    surdo: bool,
}

pub async fn contar(local: &LocalParticipant, mudo: bool, surdo: bool, para: Vec<ParticipantIdentity>) {
    let pacote = DataPacket { payload: montar(mudo, surdo), topic: None, reliable: true, destination_identities: para };
    if let Err(e) = local.publish_data(pacote).await {
        eprintln!("não consegui contar meu estado à sala: {e}");
    }
}

pub fn repassar_o_que_chegou(escritor: &Escritor, de: &str, payload: &[u8]) {
    let Some(recado) = ler(payload) else { return };
    escritor.manda(&Evento::estado_da_voz(de, "mudo", recado.mudo));
    escritor.manda(&Evento::estado_da_voz(de, "surdo", recado.surdo));
}

fn montar(mudo: bool, surdo: bool) -> Vec<u8> {
    serde_json::to_vec(&Recado { astra: RECADO_DO_ESTADO.into(), mudo, surdo }).unwrap_or_default()
}

fn ler(payload: &[u8]) -> Option<Recado> {
    serde_json::from_slice::<Recado>(payload).ok().filter(|recado| recado.astra == RECADO_DO_ESTADO)
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn recado_sai_igual_ao_do_go() {
        assert_eq!(montar(true, false), br#"{"astra":"estado-da-voz","mudo":true,"surdo":false}"#.to_vec());
    }

    #[test]
    fn le_o_recado_do_go_e_do_android() {
        let lido = ler(br#"{"astra":"estado-da-voz","mudo":false,"surdo":true}"#).unwrap();
        assert_eq!(lido, Recado { astra: RECADO_DO_ESTADO.into(), mudo: false, surdo: true });
    }

    #[test]
    fn ignora_outro_recado_ou_lixo() {
        assert!(ler(br#"{"astra":"outra-coisa","mudo":true,"surdo":true}"#).is_none());
        assert!(ler(b"isto nao e json").is_none());
    }
}
