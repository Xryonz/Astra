use super::controles::Controles;
use livekit::prelude::{RemoteParticipant, RemoteTrackPublication, Room, TrackSource};

pub fn e_tela(publicacao: &RemoteTrackPublication) -> bool {
    publicacao.source() == TrackSource::Screenshare
}

pub fn telas_de(participante: &RemoteParticipant) -> impl Iterator<Item = RemoteTrackPublication> {
    participante.track_publications().into_values().filter(e_tela)
}

pub fn ajustar_ao_palco(sala: &Room, controles: &Controles) {
    for (quem, participante) in sala.remote_participants() {
        let assinar = controles.assiste_a(quem.as_str());
        for tela in telas_de(&participante) {
            tela.set_subscribed(assinar);
        }
    }
}
