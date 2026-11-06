use livekit::prelude::{LocalAudioTrack, PlatformAudio};
use livekit::webrtc::stats::RtcStats;
use livekit::webrtc::stats::dictionaries::AudioSourceStats;
use std::time::Duration;

const ESPERA_ATE_CONFERIR: Duration = Duration::from_secs(5);

pub fn relatar_o_cancelador_do_windows(audio: &PlatformAudio) {
    eprintln!("cancelador de eco do Windows disponível: {}", audio.is_hardware_aec_available());
}

pub async fn conferir_o_eco(microfone: LocalAudioTrack) {
    tokio::time::sleep(ESPERA_ATE_CONFERIR).await;
    match microfone.get_stats().await {
        Ok(estatisticas) => estatisticas.iter().filter_map(fonte_de_audio).for_each(relatar),
        Err(e) => eprintln!("conferência do eco falhou: {e}"),
    }
}

fn fonte_de_audio(estatistica: &RtcStats) -> Option<&AudioSourceStats> {
    match estatistica {
        RtcStats::MediaSource(fonte) if fonte.source.kind == "audio" => Some(&fonte.audio),
        _ => None,
    }
}

fn relatar(audio: &AudioSourceStats) {
    eprintln!(
        "eco: perda {:.1} dB, realce {:.1} dB",
        audio.echo_return_loss, audio.echo_return_loss_enhancement
    );
}
