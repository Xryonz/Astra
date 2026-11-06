use super::controles::Controles;
use livekit::prelude::{RemoteParticipant, RemoteTrack, Room};

pub fn aplicar_a_todos(sala: &Room, controles: &Controles) {
    for (quem, participante) in sala.remote_participants() {
        aplicar_a_pessoa(&participante, controles.ouve(quem.as_str()));
    }
}

pub fn aplicar_a_faixa(faixa: &RemoteTrack, ouvir: bool) {
    let RemoteTrack::Audio(audio) = faixa else { return };
    if ouvir {
        audio.enable();
    } else {
        audio.disable();
    }
}

fn aplicar_a_pessoa(participante: &RemoteParticipant, ouvir: bool) {
    for publicacao in participante.track_publications().into_values() {
        if let Some(faixa) = publicacao.track() {
            aplicar_a_faixa(&faixa, ouvir);
        }
    }
}
