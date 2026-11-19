use super::medidas::arredondar;
use crate::com::ComDaThread;
use crate::tela::{CapturaDeJanela, Colheita, LeitorDePixel, dispositivo_da_placa, em, placas_de_hardware};
use serde::Serialize;
use std::collections::HashSet;
use std::time::{Duration, Instant};
use windows::Win32::Foundation::HWND;

#[derive(Serialize)]
struct RelatoDaJanela {
    quadros: usize,
    cores_distintas: usize,
    tamanhos: Vec<String>,
    quadros_por_segundo: f64,
    intervalo_mediano_ms: f64,
    maior_intervalo_ms: f64,
}

pub fn sondar_janela(janela: u64, segundos: u64) -> i32 {
    let _com = ComDaThread::iniciar();
    match sondar(janela, Duration::from_secs(segundos)) {
        Ok(relato) => {
            println!("{}", serde_json::to_string(&relato).unwrap_or_default());
            0
        }
        Err(msg) => {
            eprintln!("sondar a janela: {msg}");
            1
        }
    }
}

fn sondar(janela: u64, duracao: Duration) -> Result<RelatoDaJanela, String> {
    let placa = placas_de_hardware()
        .map_err(em("listar as placas"))?
        .into_iter()
        .next()
        .ok_or("nenhuma placa de vídeo de hardware")?;
    let dispositivo = dispositivo_da_placa(&placa.adaptador).map_err(em("criar o dispositivo na placa"))?;
    let mut captura = CapturaDeJanela::abrir(&dispositivo.placa, HWND(janela as *mut _))
        .map_err(em("abrir a captura da janela"))?;
    let leitor = LeitorDePixel::novo(&dispositivo).map_err(em("criar a cópia de leitura"))?;
    let (largura, altura) = captura.tamanho();
    let mut tamanhos = vec![format!("{largura}x{altura}")];
    let mut chegadas = Vec::new();
    let mut cores = HashSet::new();
    let fim = Instant::now() + duracao;
    while Instant::now() < fim {
        match captura.colher().map_err(em("colher o quadro"))? {
            Colheita::Quadro(quadro) => {
                chegadas.push(Instant::now());
                let (largura, altura) = captura.tamanho();
                let centro = (largura / 2, altura / 2);
                cores.insert(leitor.ler(&quadro.textura, centro).map_err(em("ler o centro"))?);
            }
            Colheita::MudouDeTamanho(largura, altura) => tamanhos.push(format!("{largura}x{altura}")),
            Colheita::Nada => std::thread::sleep(Duration::from_millis(1)),
        }
    }
    Ok(resumir(&chegadas, cores.len(), tamanhos, duracao))
}

fn resumir(chegadas: &[Instant], cores_distintas: usize, tamanhos: Vec<String>, duracao: Duration) -> RelatoDaJanela {
    let mut intervalos: Vec<f64> =
        chegadas.windows(2).map(|par| (par[1] - par[0]).as_secs_f64() * 1000.0).collect();
    intervalos.sort_by(f64::total_cmp);
    RelatoDaJanela {
        quadros: chegadas.len(),
        cores_distintas,
        tamanhos,
        quadros_por_segundo: arredondar(chegadas.len() as f64 / duracao.as_secs_f64()),
        intervalo_mediano_ms: arredondar(intervalos.get(intervalos.len() / 2).copied().unwrap_or_default()),
        maior_intervalo_ms: arredondar(intervalos.last().copied().unwrap_or_default()),
    }
}
