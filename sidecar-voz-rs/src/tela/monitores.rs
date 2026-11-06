use serde::Serialize;
use std::sync::Once;
use windows::Win32::Graphics::Dxgi::{CreateDXGIFactory1, DXGI_OUTPUT_DESC, IDXGIAdapter1, IDXGIFactory1};
use windows::Win32::UI::HiDpi::{DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2, SetProcessDpiAwarenessContext};
use windows::Win32::UI::WindowsAndMessaging::SetProcessDPIAware;

const SAIDAS_POR_PLACA: u32 = 8;

static PIXELS_DE_VERDADE: Once = Once::new();

#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Monitor {
    pub indice: u32,
    pub nome: String,
    pub largura: i32,
    pub altura: i32,
    pub principal: bool,
}

#[derive(Clone, Debug)]
struct Saida {
    indice_na_placa: u32,
    nome: String,
    esquerda: i32,
    topo: i32,
    direita: i32,
    base: i32,
}

pub fn listar_monitores() -> Result<Vec<Monitor>, String> {
    PIXELS_DE_VERDADE.call_once(entender_pixels_por_monitor);
    let placas = saidas_de_cada_placa().map_err(|e| e.to_string())?;
    let lista = numerar(&placas);
    if lista.is_empty() {
        return Err("nenhum monitor ligado à área de trabalho".into());
    }
    Ok(lista)
}

fn entender_pixels_por_monitor() {
    if unsafe { SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2) }.is_err() {
        let _ = unsafe { SetProcessDPIAware() };
    }
}

fn saidas_de_cada_placa() -> windows::core::Result<Vec<Vec<Saida>>> {
    let fabrica: IDXGIFactory1 = unsafe { CreateDXGIFactory1()? };
    let mut placas = Vec::new();
    while let Ok(placa) = unsafe { fabrica.EnumAdapters1(placas.len() as u32) } {
        placas.push(saidas_ligadas(&placa));
    }
    Ok(placas)
}

fn saidas_ligadas(placa: &IDXGIAdapter1) -> Vec<Saida> {
    (0..SAIDAS_POR_PLACA)
        .map_while(|i| unsafe { placa.EnumOutputs(i) }.ok().map(|saida| (i, saida)))
        .filter_map(|(i, saida)| unsafe { saida.GetDesc() }.ok().map(|desc| (i, desc)))
        .filter(|(_, desc)| desc.AttachedToDesktop.as_bool())
        .map(|(i, desc)| Saida::da_descricao(i, &desc))
        .collect()
}

fn numerar(placas: &[Vec<Saida>]) -> Vec<Monitor> {
    let mut lista = Vec::new();
    let mut proximo = 0;
    for (n, saidas) in placas.iter().enumerate() {
        for saida in saidas {
            let indice = if n == 0 { saida.indice_na_placa } else { proximo };
            proximo = proximo.max(indice + 1);
            lista.push(saida.como_monitor(indice));
        }
    }
    lista
}

impl Saida {
    fn da_descricao(indice_na_placa: u32, desc: &DXGI_OUTPUT_DESC) -> Self {
        let fim = desc.DeviceName.iter().position(|&c| c == 0).unwrap_or(desc.DeviceName.len());
        let area = desc.DesktopCoordinates;
        Self {
            indice_na_placa,
            nome: String::from_utf16_lossy(&desc.DeviceName[..fim]),
            esquerda: area.left,
            topo: area.top,
            direita: area.right,
            base: area.bottom,
        }
    }

    fn como_monitor(&self, indice: u32) -> Monitor {
        Monitor {
            indice,
            nome: self.nome.clone(),
            largura: self.direita - self.esquerda,
            altura: self.base - self.topo,
            principal: self.esquerda == 0 && self.topo == 0,
        }
    }
}

#[cfg(test)]
mod testes {
    use super::*;

    fn saida(indice_na_placa: u32, nome: &str, esquerda: i32) -> Saida {
        Saida { indice_na_placa, nome: nome.into(), esquerda, topo: 0, direita: esquerda + 1920, base: 1080 }
    }

    #[test]
    fn placa_padrao_mantem_os_numeros_do_go_e_a_outra_continua_depois() {
        let placas = vec![
            vec![saida(0, "\\\\.\\DISPLAY1", 0), saida(2, "\\\\.\\DISPLAY3", 1920)],
            vec![saida(0, "\\\\.\\DISPLAY2", 3840)],
        ];
        let indices: Vec<u32> = numerar(&placas).iter().map(|m| m.indice).collect();
        assert_eq!(indices, vec![0, 2, 3]);
    }

    #[test]
    fn principal_e_quem_comeca_na_origem_e_o_tamanho_sai_da_area() {
        let lista = numerar(&[vec![saida(0, "a", 0), saida(1, "b", 1920)]]);
        assert!(lista[0].principal);
        assert!(!lista[1].principal);
        assert_eq!((lista[0].largura, lista[0].altura), (1920, 1080));
    }

    #[test]
    fn sem_placa_nao_ha_monitor() {
        assert!(numerar(&[]).is_empty());
    }
}
