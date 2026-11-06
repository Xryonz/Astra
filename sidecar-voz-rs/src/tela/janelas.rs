use super::pixels::garantir_pixels_reais;
use serde::Serialize;
use std::ffi::c_void;
use windows::Win32::Foundation::{HWND, LPARAM, RECT};
use windows::Win32::Graphics::Dwm::{DWMWA_CLOAKED, DWMWA_EXTENDED_FRAME_BOUNDS, DwmGetWindowAttribute};
use windows::Win32::UI::WindowsAndMessaging::{
    EnumWindows, GWL_EXSTYLE, GetWindowLongPtrW, GetWindowRect, GetWindowTextLengthW, GetWindowTextW, IsIconic,
    IsWindowVisible, WS_EX_TOOLWINDOW,
};
use windows::core::BOOL;

const LARGURA_MINIMA: i32 = 160;
const ALTURA_MINIMA: i32 = 120;

#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Janela {
    pub id: u64,
    pub nome: String,
    pub largura: i32,
    pub altura: i32,
}

pub fn listar_janelas() -> Vec<Janela> {
    garantir_pixels_reais();
    todas_as_janelas().into_iter().filter_map(descrever).collect()
}

fn todas_as_janelas() -> Vec<HWND> {
    let mut janelas: Vec<HWND> = Vec::new();
    let destino = LPARAM(&mut janelas as *mut Vec<HWND> as isize);
    let _ = unsafe { EnumWindows(Some(guardar), destino) };
    janelas
}

unsafe extern "system" fn guardar(janela: HWND, destino: LPARAM) -> BOOL {
    let janelas = unsafe { &mut *(destino.0 as *mut Vec<HWND>) };
    janelas.push(janela);
    true.into()
}

fn descrever(janela: HWND) -> Option<Janela> {
    if !unsafe { IsWindowVisible(janela) }.as_bool() || unsafe { IsIconic(janela) }.as_bool() {
        return None;
    }
    if e_de_ferramenta(janela) || esta_encoberta(janela) {
        return None;
    }
    let nome = titulo(janela).filter(|nome| !nome.is_empty())?;
    let (largura, altura) = moldura_visivel(janela)?;
    tem_tamanho_minimo(largura, altura).then_some(Janela { id: janela.0 as usize as u64, nome, largura, altura })
}

fn e_de_ferramenta(janela: HWND) -> bool {
    let estilo = unsafe { GetWindowLongPtrW(janela, GWL_EXSTYLE) };
    (estilo as u32) & WS_EX_TOOLWINDOW.0 != 0
}

fn esta_encoberta(janela: HWND) -> bool {
    let mut encoberta: u32 = 0;
    let destino = &mut encoberta as *mut u32 as *mut c_void;
    let lido = unsafe { DwmGetWindowAttribute(janela, DWMWA_CLOAKED, destino, size_of::<u32>() as u32) };
    lido.is_ok() && encoberta != 0
}

fn titulo(janela: HWND) -> Option<String> {
    let tamanho = unsafe { GetWindowTextLengthW(janela) };
    if tamanho <= 0 {
        return None;
    }
    let mut texto = vec![0u16; tamanho as usize + 1];
    let lidos = unsafe { GetWindowTextW(janela, &mut texto) }.max(0) as usize;
    Some(String::from_utf16_lossy(&texto[..lidos]))
}

fn moldura_visivel(janela: HWND) -> Option<(i32, i32)> {
    let mut area = RECT::default();
    let destino = &mut area as *mut RECT as *mut c_void;
    let pelo_dwm = unsafe { DwmGetWindowAttribute(janela, DWMWA_EXTENDED_FRAME_BOUNDS, destino, size_of::<RECT>() as u32) };
    if pelo_dwm.is_err() || area.right <= area.left || area.bottom <= area.top {
        unsafe { GetWindowRect(janela, &mut area) }.ok()?;
    }
    Some((area.right - area.left, area.bottom - area.top))
}

fn tem_tamanho_minimo(largura: i32, altura: i32) -> bool {
    largura >= LARGURA_MINIMA && altura >= ALTURA_MINIMA
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn janela_pequena_fica_fora_como_no_go() {
        assert!(tem_tamanho_minimo(160, 120));
        assert!(!tem_tamanho_minimo(159, 600));
        assert!(!tem_tamanho_minimo(800, 119));
    }
}
