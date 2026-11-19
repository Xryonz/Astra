use crate::ponte::{Escritor, Evento, LeituraDoCaminho};
use livekit::prelude::LocalTrack;
use livekit::webrtc::stats::RtcStats;
use std::sync::Arc;
use std::time::Duration;
use tokio::task::JoinHandle;
use tokio::time::MissedTickBehavior;

const LER_A_CADA: Duration = Duration::from_secs(1);
const LEITURAS_POR_RELATO: u32 = 5;

#[derive(Clone, Copy, Debug, Default, PartialEq)]
struct Amostra {
    ida_agora: f64,
    ida_somada: f64,
    medidas: u64,
    tremor: f64,
    perda: f64,
}

#[derive(Default)]
struct Medidor {
    inicio: Option<Amostra>,
    pico: f64,
    tremor: f64,
    perda: f64,
    leituras: u32,
}

impl Medidor {
    fn anotar(&mut self, amostra: Amostra) -> Option<LeituraDoCaminho> {
        let inicio = *self.inicio.get_or_insert(amostra);
        self.pico = self.pico.max(amostra.ida_agora);
        self.tremor = self.tremor.max(amostra.tremor);
        self.perda = self.perda.max(amostra.perda);
        self.leituras += 1;
        if self.leituras < LEITURAS_POR_RELATO {
            return None;
        }
        let leitura = self.fechar(inicio, amostra);
        *self = Self { inicio: Some(amostra), ..Self::default() };
        leitura
    }

    fn fechar(&self, inicio: Amostra, fim: Amostra) -> Option<LeituraDoCaminho> {
        let medidas = fim.medidas.checked_sub(inicio.medidas).filter(|&medidas| medidas > 0)?;
        let media = (fim.ida_somada - inicio.ida_somada) / medidas as f64;
        Some(LeituraDoCaminho {
            ida: milissegundos(media),
            pico: milissegundos(self.pico.max(media)),
            tremor: milissegundos(self.tremor),
            perda: (self.perda * 100.0).round() as i64,
        })
    }
}

fn milissegundos(segundos: f64) -> i64 {
    (segundos * 1000.0).round().max(0.0) as i64
}

fn amostra_das(estatisticas: &[RtcStats]) -> Option<Amostra> {
    estatisticas.iter().find_map(|estatistica| match estatistica {
        RtcStats::RemoteInboundRtp(recebida) => Some(Amostra {
            ida_agora: recebida.remote_inbound.round_trip_time,
            ida_somada: recebida.remote_inbound.total_round_trip_time,
            medidas: recebida.remote_inbound.round_trip_time_measurements,
            tremor: recebida.received.jitter,
            perda: recebida.remote_inbound.fraction_lost,
        }),
        _ => None,
    })
}

pub struct Medicao(JoinHandle<()>);

impl Medicao {
    pub fn comecar(escritor: Arc<Escritor>, tipo: &'static str, faixa: LocalTrack) -> Self {
        Self(tokio::spawn(medir_o_caminho(escritor, tipo, faixa)))
    }
}

impl Drop for Medicao {
    fn drop(&mut self) {
        self.0.abort();
    }
}

pub async fn medir_o_caminho(escritor: Arc<Escritor>, tipo: &'static str, faixa: LocalTrack) {
    let mut medidor = Medidor::default();
    let mut relogio = tokio::time::interval(LER_A_CADA);
    relogio.set_missed_tick_behavior(MissedTickBehavior::Delay);
    loop {
        relogio.tick().await;
        let Ok(estatisticas) = faixa.get_stats().await else { continue };
        let Some(amostra) = amostra_das(&estatisticas) else { continue };
        if let Some(leitura) = medidor.anotar(amostra) {
            relatar(&escritor, tipo, leitura);
        }
    }
}

fn relatar(escritor: &Escritor, tipo: &str, leitura: LeituraDoCaminho) {
    eprintln!(
        "caminho da {tipo} · ida e volta {} ms (pico {}) · tremor {} ms · perda {}%",
        leitura.ida, leitura.pico, leitura.tremor, leitura.perda
    );
    escritor.manda(&Evento::caminho(tipo, leitura));
}

#[cfg(test)]
mod testes {
    use super::*;
    use livekit::webrtc::stats::RemoteInboundRtpStats;
    use livekit::webrtc::stats::dictionaries::{ReceivedRtpStreamStats, RemoteInboundRtpStreamStats};

    fn amostra(ida_agora: f64, ida_somada: f64, medidas: u64) -> Amostra {
        Amostra { ida_agora, ida_somada, medidas, tremor: 0.0, perda: 0.0 }
    }

    #[test]
    fn a_leitura_sai_na_quinta_amostra_com_a_media_do_intervalo() {
        let mut medidor = Medidor::default();
        let amostras = [
            amostra(0.040, 1.00, 10),
            amostra(0.050, 1.05, 11),
            amostra(0.080, 1.13, 12),
            amostra(0.040, 1.17, 13),
            amostra(0.030, 1.20, 14),
        ];
        let leituras: Vec<_> = amostras.into_iter().map(|a| medidor.anotar(a)).collect();
        assert!(leituras[..4].iter().all(Option::is_none));
        assert_eq!(leituras[4], Some(LeituraDoCaminho { ida: 50, pico: 80, tremor: 0, perda: 0 }));
    }

    #[test]
    fn tremor_e_perda_sao_os_piores_do_intervalo() {
        let mut medidor = Medidor::default();
        let piores = [(0.002, 0.0), (0.009, 0.01), (0.004, 0.035), (0.001, 0.0), (0.003, 0.02)];
        let leituras: Vec<_> = (0u64..)
            .zip(piores)
            .map(|(n, (tremor, perda))| medidor.anotar(Amostra { tremor, perda, ..amostra(0.04, 0.04 * n as f64, n) }))
            .collect();
        assert_eq!(leituras[4].map(|l| (l.tremor, l.perda)), Some((9, 4)));
    }

    #[test]
    fn sem_medida_nova_no_intervalo_nao_ha_leitura() {
        let mut medidor = Medidor::default();
        let leituras: Vec<_> = (0..5).map(|_| medidor.anotar(amostra(0.04, 1.0, 10))).collect();
        assert!(leituras.iter().all(Option::is_none));
    }

    #[test]
    fn o_intervalo_seguinte_comeca_onde_o_anterior_terminou() {
        let mut medidor = Medidor::default();
        for n in 0u64..5 {
            medidor.anotar(amostra(0.040, 0.040 * n as f64, n));
        }
        let segunda: Vec<_> =
            (5u64..10).map(|n| medidor.anotar(amostra(0.100, 0.16 + 0.100 * (n - 4) as f64, n))).collect();
        assert_eq!(segunda[4].map(|l| l.ida), Some(100));
    }

    #[test]
    fn a_amostra_vem_do_relato_de_quem_recebe() {
        let recebida = RemoteInboundRtpStats {
            remote_inbound: RemoteInboundRtpStreamStats {
                round_trip_time: 0.05,
                total_round_trip_time: 2.0,
                round_trip_time_measurements: 40,
                fraction_lost: 0.01,
                ..Default::default()
            },
            received: ReceivedRtpStreamStats { jitter: 0.004, ..Default::default() },
            ..Default::default()
        };
        let estatisticas = [RtcStats::Track, RtcStats::RemoteInboundRtp(recebida)];
        assert_eq!(
            amostra_das(&estatisticas),
            Some(Amostra { ida_agora: 0.05, ida_somada: 2.0, medidas: 40, tremor: 0.004, perda: 0.01 })
        );
        assert_eq!(amostra_das(&[]), None);
    }
}
