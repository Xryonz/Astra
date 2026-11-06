use livekit::options::{AudioEncoding, TrackPublishOptions};
use livekit::prelude::{LocalAudioTrack, LocalTrack, PlatformAudio, Room, TrackSource};

const NOME_DO_MICROFONE: &str = "astra-microfone";
const TAXA_DA_VOZ_IGUAL_AO_GO: u64 = 64_000;

pub fn gravar_sem_o_cancelador_do_windows(audio: &PlatformAudio) {
    if let Err(e) = audio.set_echo_cancellation(true, false) {
        eprintln!("não consegui desligar o cancelador de eco do Windows: {e}");
    }
    if let Err(e) = audio.start_recording() {
        eprintln!("não consegui abrir a gravação antes de publicar: {e}");
    }
}

pub fn abrir_ou_fechar(microfone: &LocalAudioTrack, aberto: bool) {
    if aberto {
        microfone.enable();
    } else {
        microfone.disable();
    }
}

pub async fn publicar_o_microfone(
    sala: &Room,
    audio: &PlatformAudio,
    aberto: bool,
) -> Result<LocalAudioTrack, String> {
    let faixa = LocalAudioTrack::create_audio_track(NOME_DO_MICROFONE, audio.rtc_source());
    abrir_ou_fechar(&faixa, aberto);
    sala.local_participant()
        .publish_track(LocalTrack::Audio(faixa.clone()), opcoes_da_voz())
        .await
        .map(|_| faixa)
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
