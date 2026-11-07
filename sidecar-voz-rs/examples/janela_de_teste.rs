use std::sync::atomic::{AtomicU32, Ordering};
use std::time::{Duration, Instant};
use windows::Win32::Foundation::{COLORREF, HWND, LPARAM, LRESULT, RECT, WPARAM};
use windows::Win32::Graphics::Gdi::{
    BeginPaint, CreateSolidBrush, DeleteObject, EndPaint, FillRect, HDC, InvalidateRect, PAINTSTRUCT,
};
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::HiDpi::{DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2, SetProcessDpiAwarenessContext};
use windows::Win32::UI::WindowsAndMessaging::{
    AdjustWindowRectEx, CreateWindowExW, DefWindowProcW, DestroyWindow, DispatchMessageW, GetClientRect, MSG,
    PM_REMOVE, PeekMessageW, PostQuitMessage, RegisterClassW, SW_SHOWMINNOACTIVE, SW_SHOWNOACTIVATE, SWP_NOACTIVATE,
    SWP_NOMOVE, SWP_NOZORDER, SetWindowPos, ShowWindow, TranslateMessage, WINDOW_EX_STYLE, WINDOW_STYLE,
    WM_DESTROY, WM_PAINT, WM_QUIT, WNDCLASSW, WS_POPUP,
};
use windows::core::w;

const ESTILO: WINDOW_STYLE = WS_POPUP;
const INTERVALO: Duration = Duration::from_micros(16_667);
const METADE_DO_CENTRO: i32 = 120;
const ROTEIRO: [(u64, Passo); 3] = [(4, Passo::Encolher), (6, Passo::Minimizar), (8, Passo::Restaurar)];
static QUADRO: AtomicU32 = AtomicU32::new(0);

#[derive(Clone, Copy)]
enum Passo {
    Encolher,
    Minimizar,
    Restaurar,
}

struct Pedido {
    largura: i32,
    altura: i32,
    x: i32,
    y: i32,
    segundos: u64,
    com_roteiro: bool,
}

fn main() {
    let pedido = ler_o_pedido();
    let _ = unsafe { SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2) };
    let janela = abrir_a_janela(&pedido).expect("abrir a janela de teste");
    println!("{}", janela.0 as usize);
    animar(janela, &pedido);
}

fn ler_o_pedido() -> Pedido {
    let argumentos: Vec<String> = std::env::args().skip(1).collect();
    let numero = |indice: usize, padrao: i32| argumentos.get(indice).and_then(|texto| texto.parse().ok()).unwrap_or(padrao);
    Pedido {
        largura: numero(0, 1280),
        altura: numero(1, 720),
        x: numero(2, -3000),
        y: numero(3, 0),
        segundos: numero(4, 20).max(1) as u64,
        com_roteiro: argumentos.iter().any(|argumento| argumento == "--roteiro"),
    }
}

fn abrir_a_janela(pedido: &Pedido) -> windows::core::Result<HWND> {
    let instancia = unsafe { GetModuleHandleW(None)? };
    let classe = WNDCLASSW {
        lpfnWndProc: Some(procedimento),
        hInstance: instancia.into(),
        lpszClassName: w!("AstraJanelaDeTeste"),
        ..Default::default()
    };
    unsafe { RegisterClassW(&classe) };
    let (largura, altura) = tamanho_por_fora(pedido.largura, pedido.altura);
    let janela = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE(0),
            w!("AstraJanelaDeTeste"),
            w!("Astra — janela de teste"),
            ESTILO,
            pedido.x,
            pedido.y,
            largura,
            altura,
            None,
            None,
            Some(instancia.into()),
            None,
        )?
    };
    let _ = unsafe { ShowWindow(janela, SW_SHOWNOACTIVATE) };
    Ok(janela)
}

fn tamanho_por_fora(largura: i32, altura: i32) -> (i32, i32) {
    let mut area = RECT { left: 0, top: 0, right: largura, bottom: altura };
    let _ = unsafe { AdjustWindowRectEx(&mut area, ESTILO, false, WINDOW_EX_STYLE(0)) };
    (area.right - area.left, area.bottom - area.top)
}

fn animar(janela: HWND, pedido: &Pedido) {
    let comeco = Instant::now();
    let mut vez = comeco;
    let mut passos_feitos = 0;
    while atender_as_mensagens() {
        let passou = comeco.elapsed();
        if passou >= Duration::from_secs(pedido.segundos) {
            let _ = unsafe { DestroyWindow(janela) };
            return;
        }
        if pedido.com_roteiro
            && let Some(&(segundo, passo)) = ROTEIRO.get(passos_feitos)
            && passou >= Duration::from_secs(segundo)
        {
            dar_o_passo(janela, pedido, passo);
            passos_feitos += 1;
        }
        if let Some(falta) = vez.checked_duration_since(Instant::now()) {
            std::thread::sleep(falta);
            continue;
        }
        QUADRO.fetch_add(1, Ordering::Relaxed);
        let _ = unsafe { InvalidateRect(Some(janela), None, false) };
        vez += INTERVALO;
    }
}

fn atender_as_mensagens() -> bool {
    let mut mensagem = MSG::default();
    while unsafe { PeekMessageW(&mut mensagem, None, 0, 0, PM_REMOVE) }.as_bool() {
        if mensagem.message == WM_QUIT {
            return false;
        }
        unsafe {
            let _ = TranslateMessage(&mensagem);
            DispatchMessageW(&mensagem);
        }
    }
    true
}

fn dar_o_passo(janela: HWND, pedido: &Pedido, passo: Passo) {
    match passo {
        Passo::Encolher => {
            let (largura, altura) = tamanho_por_fora(pedido.largura * 3 / 4, pedido.altura * 3 / 4);
            let _ = unsafe {
                SetWindowPos(janela, None, 0, 0, largura, altura, SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE)
            };
        }
        Passo::Minimizar => {
            let _ = unsafe { ShowWindow(janela, SW_SHOWMINNOACTIVE) };
        }
        Passo::Restaurar => {
            let _ = unsafe { ShowWindow(janela, SW_SHOWNOACTIVATE) };
        }
    }
}

unsafe extern "system" fn procedimento(janela: HWND, mensagem: u32, w: WPARAM, l: LPARAM) -> LRESULT {
    match mensagem {
        WM_PAINT => {
            pintar(janela);
            LRESULT(0)
        }
        WM_DESTROY => {
            unsafe { PostQuitMessage(0) };
            LRESULT(0)
        }
        _ => unsafe { DefWindowProcW(janela, mensagem, w, l) },
    }
}

fn pintar(janela: HWND) {
    let mut pintura = PAINTSTRUCT::default();
    let dc = unsafe { BeginPaint(janela, &mut pintura) };
    let mut area = RECT::default();
    let _ = unsafe { GetClientRect(janela, &mut area) };
    let quadro = QUADRO.load(Ordering::Relaxed);
    preencher(dc, &area, cor(30, 30, (quadro / 2 % 200) as u8 + 30));
    pintar_as_faixas(dc, &area, quadro);
    let (x, y) = (area.right / 2, area.bottom / 2);
    let centro = RECT {
        left: x - METADE_DO_CENTRO,
        top: y - METADE_DO_CENTRO,
        right: x + METADE_DO_CENTRO,
        bottom: y + METADE_DO_CENTRO,
    };
    preencher(dc, &centro, cor((quadro & 0xFF) as u8, ((quadro >> 8) & 0xFF) as u8, 0x40));
    let _ = unsafe { EndPaint(janela, &pintura) };
}

fn pintar_as_faixas(dc: HDC, area: &RECT, quadro: u32) {
    let largura = area.right.max(1);
    let altura_da_faixa = (area.bottom / 8).max(1);
    for faixa in 0..8 {
        let x = (quadro as i32).wrapping_mul(3 + faixa).rem_euclid(largura);
        let y = altura_da_faixa * faixa;
        let retangulo = RECT { left: x, top: y, right: x + largura / 10, bottom: y + altura_da_faixa };
        preencher(dc, &retangulo, cor((faixa * 30) as u8, 200, 120));
    }
}

fn preencher(dc: HDC, area: &RECT, cor: COLORREF) {
    unsafe {
        let pincel = CreateSolidBrush(cor);
        FillRect(dc, area, pincel);
        let _ = DeleteObject(pincel.into());
    }
}

fn cor(vermelho: u8, verde: u8, azul: u8) -> COLORREF {
    COLORREF(u32::from(vermelho) | (u32::from(verde) << 8) | (u32::from(azul) << 16))
}
