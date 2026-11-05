use livekit::options::{AudioEncoding, TrackPublishOptions};
use livekit::prelude::{LocalAudioTrack, LocalTrack, PlatformAudio, Room, TrackSource};

const NOME_DO_MICROFONE: &str = "astra-microfone";
const TAXA_DA_VOZ_IGUAL_AO_GO: u64 = 64_000;

pub async fn publicar_o_microfone(sala: &Room, audio: &PlatformAudio) -> Result<(), String> {
    let faixa = LocalAudioTrack::create_audio_track(NOME_DO_MICROFONE, audio.rtc_source());
    sala.local_participant()
        .publish_track(LocalTrack::Audio(faixa), opcoes_da_voz())
        .await
        .map(|_| ())
        .map_err(|e| format!("publicar microfone: {e}"))
}

fn opcoes_da_voz() -> TrackPublishOptions {
    TrackPublishOptions {
        source: TrackSource::Microphone,
        audio_encoding: Some(AudioEncoding { max_bitrate: TAXA_DA_VOZ_IGUAL_AO_GO }),
        dtx: true,
        red: false,
        ..Default::default()
    }
}
