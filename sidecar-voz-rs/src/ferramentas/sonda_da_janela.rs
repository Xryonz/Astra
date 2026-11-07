use super::medidas::arredondar;
use crate::com::ComDaThread;
use crate::tela::{CapturaDeJanela, Colheita, Dispositivo, dispositivo_da_placa, em, placas_de_hardware};
use serde::Serialize;
use std::collections::HashSet;
use std::time::{Duration, Instant};
use windows::Win32::Foundation::{E_POINTER, HWND};
use windows::Win32::Graphics::Direct3D11::{
    D3D11_BOX, D3D11_CPU_ACCESS_READ, D3D11_MAP_READ, D3D11_MAPPED_SUBRESOURCE, D3D11_TEXTURE2D_DESC,
    D3D11_USAGE_STAGING, ID3D11DeviceContext, ID3D11Texture2D,
};
use windows::Win32::Graphics::Dxgi::Common::{DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_SAMPLE_DESC};

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
    let leitor = LeitorDoCentro::novo(&dispositivo).map_err(em("criar a cópia de leitura"))?;
    let (largura, altura) = captura.tamanho();
    let mut tamanhos = vec![format!("{largura}x{altura}")];
    let mut chegadas = Vec::new();
    let mut cores = HashSet::new();
    let fim = Instant::now() + duracao;
    while Instant::now() < fim {
        match captura.colher().map_err(em("colher o quadro"))? {
            Colheita::Quadro(quadro) => {
                chegadas.push(Instant::now());
                cores.insert(leitor.ler(&quadro.textura, captura.tamanho()).map_err(em("ler o centro"))?);
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

struct LeitorDoCentro {
    contexto: ID3D11DeviceContext,
    copia: ID3D11Texture2D,
}

impl LeitorDoCentro {
    fn novo(dispositivo: &Dispositivo) -> windows::core::Result<Self> {
        let desc = D3D11_TEXTURE2D_DESC {
            Width: 1,
            Height: 1,
            MipLevels: 1,
            ArraySize: 1,
            Format: DXGI_FORMAT_B8G8R8A8_UNORM,
            SampleDesc: DXGI_SAMPLE_DESC { Count: 1, Quality: 0 },
            Usage: D3D11_USAGE_STAGING,
            BindFlags: 0,
            CPUAccessFlags: D3D11_CPU_ACCESS_READ.0 as u32,
            MiscFlags: 0,
        };
        let mut copia = None;
        unsafe { dispositivo.placa.CreateTexture2D(&desc, None, Some(&mut copia))? };
        Ok(Self { contexto: dispositivo.contexto.clone(), copia: copia.ok_or(E_POINTER)? })
    }

    fn ler(&self, textura: &ID3D11Texture2D, (largura, altura): (u32, u32)) -> windows::core::Result<u32> {
        let (x, y) = (largura / 2, altura / 2);
        let caixa = D3D11_BOX { left: x, top: y, front: 0, right: x + 1, bottom: y + 1, back: 1 };
        let mut mapa = D3D11_MAPPED_SUBRESOURCE::default();
        unsafe {
            self.contexto.CopySubresourceRegion(&self.copia, 0, 0, 0, 0, textura, 0, Some(&caixa));
            self.contexto.Map(&self.copia, 0, D3D11_MAP_READ, 0, Some(&mut mapa))?;
            let cor = *(mapa.pData as *const u32);
            self.contexto.Unmap(&self.copia, 0);
            Ok(cor)
        }
    }
}
