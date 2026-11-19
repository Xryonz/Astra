use livekit::options::{AudioEncoding, TrackPublishOptions};
use livekit::prelude::{LocalAudioTrack, LocalTrack, Room, RoomOptions, TrackSource};
use livekit::webrtc::audio_frame::AudioFrame;
use livekit::webrtc::audio_source::native::NativeAudioSource;
use livekit::webrtc::audio_source::{AudioSourceOptions, RtcAudioSource};
use std::f64::consts::TAU;

const TAXA: u32 = 48_000;
const AMOSTRAS_POR_QUADRO: usize = 480;
const FILA_EM_MS: u32 = 100;
const VOLUME: f64 = 900.0;
const TAXA_DA_VOZ: u64 = 64_000;

#[tokio::main]
async fn main() {
    let mut argumentos = std::env::args().skip(1);
    let (Some(url), Some(credencial)) = (argumentos.next(), argumentos.next()) else {
        eprintln!("uso: locutor <url> <credencial>");
        std::process::exit(2);
    };
    let fonte = NativeAudioSource::new(sem_tratamento(), TAXA, 1, FILA_EM_MS);
    let sala = entrar(&url, &credencial).await;
    publicar(&sala, &fonte).await;
    eprintln!("locutor no ar");
    falar(&fonte).await;
}

async fn entrar(url: &str, credencial: &str) -> Room {
    let mut opcoes = RoomOptions::default();
    opcoes.auto_subscribe = false;
    let (sala, mut eventos) = Room::connect(url, credencial, opcoes).await.expect("entrar na sala");
    tokio::spawn(async move { while eventos.recv().await.is_some() {} });
    sala
}

async fn publicar(sala: &Room, fonte: &NativeAudioSource) {
    let faixa = LocalAudioTrack::create_audio_track("locutor", RtcAudioSource::Native(fonte.clone()));
    let opcoes = TrackPublishOptions {
        source: TrackSource::Microphone,
        audio_encoding: Some(AudioEncoding { max_bitrate: TAXA_DA_VOZ }),
        dtx: false,
        red: false,
        ..Default::default()
    };
    sala.local_participant().publish_track(LocalTrack::Audio(faixa), opcoes).await.expect("publicar a voz");
}

fn sem_tratamento() -> AudioSourceOptions {
    AudioSourceOptions { echo_cancellation: false, noise_suppression: false, auto_gain_control: false }
}

async fn falar(fonte: &NativeAudioSource) {
    let mut voz = Voz::default();
    let mut amostras = vec![0i16; AMOSTRAS_POR_QUADRO];
    loop {
        voz.preencher(&mut amostras);
        let quadro = AudioFrame {
            data: amostras.as_slice().into(),
            sample_rate: TAXA,
            num_channels: 1,
            samples_per_channel: AMOSTRAS_POR_QUADRO as u32,
        };
        if fonte.capture_frame(&quadro).await.is_err() {
            break;
        }
    }
}

#[derive(Default)]
struct Voz {
    amostra: u64,
    fase: f64,
}

impl Voz {
    fn preencher(&mut self, amostras: &mut [i16]) {
        for saida in amostras {
            let segundos = self.amostra as f64 / f64::from(TAXA);
            let fundamental = 145.0 + 35.0 * (TAU * 0.3 * segundos).sin();
            self.fase = (self.fase + TAU * fundamental / f64::from(TAXA)) % TAU;
            let tom: f64 = (1..=8).map(|k| (self.fase * f64::from(k)).sin() / f64::from(k)).sum();
            let silaba = 0.55 + 0.45 * (TAU * 4.0 * segundos).sin();
            *saida = (tom * silaba * VOLUME) as i16;
            self.amostra += 1;
        }
    }
}
