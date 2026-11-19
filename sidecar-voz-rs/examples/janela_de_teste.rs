use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::time::{Duration, Instant};
use windows::Win32::Foundation::{COLORREF, HWND, LPARAM, LRESULT, RECT, WPARAM};
use windows::Win32::Graphics::Gdi::{
    BI_RGB, BITMAPINFO, BITMAPINFOHEADER, BeginPaint, CreateSolidBrush, DIB_RGB_COLORS, DeleteObject, EndPaint,
    FillRect, HDC, InvalidateRect, PAINTSTRUCT, SetDIBitsToDevice,
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
const ROTEIRO_DA_PARADA: [(u64, Passo); 2] = [(3, Passo::Congelar), (15, Passo::Afinar)];
const LADO_DO_BLOCO_DE_RUIDO: usize = 8;
static QUADRO: AtomicU32 = AtomicU32::new(0);
static RUIDO: AtomicBool = AtomicBool::new(false);

#[derive(Clone, Copy)]
enum Passo {
    Encolher,
    Minimizar,
    Restaurar,
    Congelar,
    Afinar,
}

struct Pedido {
    largura: i32,
    altura: i32,
    x: i32,
    y: i32,
    segundos: u64,
    roteiro: &'static [(u64, Passo)],
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
    let pediu = |opcao: &str| argumentos.iter().any(|argumento| argumento == opcao);
    RUIDO.store(pediu("--ruido"), Ordering::Relaxed);
    let roteiro: &'static [(u64, Passo)] = if pediu("--roteiro") {
        &ROTEIRO
    } else if pediu("--parada") {
        &ROTEIRO_DA_PARADA
    } else {
        &[]
    };
    Pedido {
        largura: numero(0, 1280),
        altura: numero(1, 720),
        x: numero(2, -3000),
        y: numero(3, 0),
        segundos: numero(4, 20).max(1) as u64,
        roteiro,
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
    let mut animando = true;
    while atender_as_mensagens() {
        let passou = comeco.elapsed();
        if passou >= Duration::from_secs(pedido.segundos) {
            let _ = unsafe { DestroyWindow(janela) };
            return;
        }
        if let Some(&(segundo, passo)) = pedido.roteiro.get(passos_feitos)
            && passou >= Duration::from_secs(segundo)
        {
            animando = dar_o_passo(janela, pedido, passo);
            passos_feitos += 1;
        }
        if let Some(falta) = vez.checked_duration_since(Instant::now()) {
            std::thread::sleep(falta);
            continue;
        }
        if animando {
            QUADRO.fetch_add(1, Ordering::Relaxed);
            let _ = unsafe { InvalidateRect(Some(janela), None, false) };
        }
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

fn dar_o_passo(janela: HWND, pedido: &Pedido, passo: Passo) -> bool {
    match passo {
        Passo::Encolher => redimensionar(janela, pedido.largura * 3 / 4, pedido.altura * 3 / 4),
        Passo::Minimizar => {
            let _ = unsafe { ShowWindow(janela, SW_SHOWMINNOACTIVE) };
        }
        Passo::Restaurar => {
            let _ = unsafe { ShowWindow(janela, SW_SHOWNOACTIVATE) };
        }
        Passo::Congelar => return false,
        Passo::Afinar => redimensionar(janela, pedido.largura * 3 / 4, pedido.altura),
    }
    true
}

fn redimensionar(janela: HWND, largura: i32, altura: i32) {
    let (largura, altura) = tamanho_por_fora(largura, altura);
    let _ = unsafe { SetWindowPos(janela, None, 0, 0, largura, altura, SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE) };
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
    if RUIDO.load(Ordering::Relaxed) {
        pintar_o_ruido(dc, &area, quadro);
        let _ = unsafe { EndPaint(janela, &pintura) };
        return;
    }
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

fn pintar_o_ruido(dc: HDC, area: &RECT, quadro: u32) {
    let (largura, altura) = (area.right.max(1) as usize, area.bottom.max(1) as usize);
    let colunas = largura.div_ceil(LADO_DO_BLOCO_DE_RUIDO);
    let mut semente = quadro.wrapping_mul(0x9E37_79B9) | 1;
    let cores: Vec<u32> = (0..colunas * altura.div_ceil(LADO_DO_BLOCO_DE_RUIDO)).map(|_| sortear(&mut semente)).collect();
    let mut pixels = vec![0u32; largura * altura];
    for (y, linha) in pixels.chunks_exact_mut(largura).enumerate() {
        let fileira = &cores[y / LADO_DO_BLOCO_DE_RUIDO * colunas..][..colunas];
        for (pedaco, &cor) in linha.chunks_mut(LADO_DO_BLOCO_DE_RUIDO).zip(fileira) {
            pedaco.fill(cor);
        }
    }
    let formato = BITMAPINFO {
        bmiHeader: BITMAPINFOHEADER {
            biSize: size_of::<BITMAPINFOHEADER>() as u32,
            biWidth: largura as i32,
            biHeight: -(altura as i32),
            biPlanes: 1,
            biBitCount: 32,
            biCompression: BI_RGB.0,
            ..Default::default()
        },
        ..Default::default()
    };
    unsafe {
        SetDIBitsToDevice(
            dc,
            0,
            0,
            largura as u32,
            altura as u32,
            0,
            0,
            0,
            altura as u32,
            pixels.as_ptr().cast(),
            &formato,
            DIB_RGB_COLORS,
        )
    };
}

fn sortear(semente: &mut u32) -> u32 {
    *semente ^= *semente << 13;
    *semente ^= *semente >> 17;
    *semente ^= *semente << 5;
    *semente
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
