use serde::Serialize;
use std::time::Duration;
use windows::Win32::Foundation::FILETIME;
use windows::Win32::System::Threading::{GetCurrentProcess, GetProcessTimes};

#[derive(Default)]
pub struct Custos {
    pub desenho: Vec<Duration>,
    pub preparo: Vec<Duration>,
    pub entrega: Vec<Duration>,
    pub saida: Vec<Duration>,
    pub latencia: Vec<Duration>,
}

#[derive(Serialize)]
pub struct Resumo {
    mediana: f64,
    p95: f64,
}

#[derive(Serialize)]
pub struct ResumoDosCustos {
    desenho: Resumo,
    preparo: Resumo,
    entrega: Resumo,
    saida: Resumo,
    latencia: Resumo,
}

impl Custos {
    pub fn resumir(&self) -> ResumoDosCustos {
        ResumoDosCustos {
            desenho: resumir(&self.desenho),
            preparo: resumir(&self.preparo),
            entrega: resumir(&self.entrega),
            saida: resumir(&self.saida),
            latencia: resumir(&self.latencia),
        }
    }
}

pub fn arredondar(valor: f64) -> f64 {
    (valor * 10.0).round() / 10.0
}

pub fn tempo_de_cpu() -> Duration {
    let [mut criado, mut saiu, mut nucleo, mut usuario] = [FILETIME::default(); 4];
    let lido = unsafe { GetProcessTimes(GetCurrentProcess(), &mut criado, &mut saiu, &mut nucleo, &mut usuario) };
    if lido.is_err() {
        return Duration::ZERO;
    }
    Duration::from_nanos((cem_nanos(nucleo) + cem_nanos(usuario)) * 100)
}

fn resumir(amostras: &[Duration]) -> Resumo {
    let mut ordenadas = amostras.to_vec();
    ordenadas.sort_unstable();
    Resumo { mediana: microssegundos_em(&ordenadas, 0.5), p95: microssegundos_em(&ordenadas, 0.95) }
}

fn microssegundos_em(ordenadas: &[Duration], fracao: f64) -> f64 {
    let Some(ultima) = ordenadas.len().checked_sub(1) else { return 0.0 };
    let posicao = (ultima as f64 * fracao).round() as usize;
    arredondar(ordenadas[posicao].as_secs_f64() * 1e6)
}

fn cem_nanos(tempo: FILETIME) -> u64 {
    (u64::from(tempo.dwHighDateTime) << 32) | u64::from(tempo.dwLowDateTime)
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn mediana_e_p95_saem_das_amostras_ordenadas() {
        let amostras: Vec<Duration> = (1..=100).rev().map(Duration::from_micros).collect();
        let resumo = resumir(&amostras);
        assert_eq!((resumo.mediana, resumo.p95), (51.0, 95.0));
    }
}
