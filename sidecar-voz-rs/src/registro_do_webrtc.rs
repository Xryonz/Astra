use log::{LevelFilter, Log, Metadata, Record};

const ALVO_DO_WEBRTC: &str = "libwebrtc";
const MARCAS_DO_AUDIO: [&str; 9] = [
    "audio_processing_controller",
    "audio_processing_impl",
    "audio_device_core_win",
    "audio_device_buffer",
    "adm_proxy",
    "audio_state",
    "webrtc_voice_engine",
    "echo_canceller",
    "kHz",
];

static REGISTRO: RegistroDoWebrtc = RegistroDoWebrtc;

struct RegistroDoWebrtc;

pub fn ligar() {
    if log::set_logger(&REGISTRO).is_ok() {
        log::set_max_level(LevelFilter::Debug);
    }
}

fn trata_do_audio(linha: &str) -> bool {
    MARCAS_DO_AUDIO.iter().any(|marca| linha.contains(marca))
}

impl Log for RegistroDoWebrtc {
    fn enabled(&self, metadata: &Metadata) -> bool {
        metadata.target() == ALVO_DO_WEBRTC
    }

    fn log(&self, record: &Record) {
        if !self.enabled(record.metadata()) {
            return;
        }
        let linha = record.args().to_string();
        if trata_do_audio(&linha) {
            eprintln!("webrtc {}", linha.trim_end());
        }
    }

    fn flush(&self) {}
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn passa_so_linha_do_audio() {
        assert!(trata_do_audio("(audio_device_core_win.cc:3316): capturing thread has ended pre-maturely"));
        assert!(trata_do_audio("[REC : 10000msec, 48kHz] callbacks: 1000"));
        assert!(!trata_do_audio("(rtcp_receiver.cc:812): Incoming PLI from SSRC 1"));
    }
}
