use super::captura_de_janela::{CapturaDeJanela, Colheita, QuadroDaJanela};
use super::dispositivo::{Dispositivo, copiar_linhas, dispositivo_da_placa, textura, textura_de_leitura};
use super::duplicacao::{CapturaDoMonitor, ColheitaDoMonitor, QuadroDoMonitor};
use super::encaixe::Encaixe;
use super::etapa::em;
use super::monitores::achar_o_monitor;
use super::placas::placa_padrao;
use super::png::png_de_bgrx;
use std::time::Duration;
use windows::Win32::Foundation::HWND;
use windows::Win32::Graphics::Direct3D11::{D3D11_MAP_READ, D3D11_MAPPED_SUBRESOURCE, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

const LARGURA_DA_MINIATURA: u32 = 256;
const TENTATIVAS: usize = 8;
const TENTATIVAS_NO_MONITOR: usize = 6;
const ESPERA_POR_TENTATIVA: Duration = Duration::from_millis(120);

pub fn miniaturas_das_janelas(janelas: &[u64], mut pronta: impl FnMut(u64, Vec<u8>)) {
    let Ok(dispositivo) = placa_padrao().and_then(|placa| dispositivo_da_placa(&placa.adaptador)) else { return };
    for &janela in janelas {
        if let Ok(png) = miniatura_da_janela(&dispositivo, janela) {
            pronta(janela, png);
        }
    }
}

fn miniatura_da_janela(dispositivo: &Dispositivo, janela: u64) -> Result<Vec<u8>, String> {
    let mut captura =
        CapturaDeJanela::abrir(&dispositivo.placa, HWND(janela as *mut _)).map_err(em("abrir a captura"))?;
    let quadro = primeiro_quadro(&mut captura)?.ok_or("a janela não entregou quadro")?;
    png_da_textura(dispositivo, &quadro.textura, captura.tamanho())
}

fn primeiro_quadro(captura: &mut CapturaDeJanela) -> Result<Option<QuadroDaJanela>, String> {
    for _ in 0..TENTATIVAS {
        if let Colheita::Quadro(quadro) = captura.colher().map_err(em("colher o quadro"))? {
            return Ok(Some(quadro));
        }
        captura.esperar(ESPERA_POR_TENTATIVA);
    }
    Ok(None)
}

pub fn miniaturas_dos_monitores(monitores: &[u32], mut pronta: impl FnMut(u32, Vec<u8>)) {
    for &monitor in monitores {
        if let Ok(png) = miniatura_do_monitor(monitor) {
            pronta(monitor, png);
        }
    }
}

fn miniatura_do_monitor(monitor: u32) -> Result<Vec<u8>, String> {
    let (placa, saida) = achar_o_monitor(monitor)?;
    let dispositivo = dispositivo_da_placa(&placa.adaptador).map_err(em("abrir a placa do monitor"))?;
    let mut captura = CapturaDoMonitor::abrir(&dispositivo.placa, saida).map_err(em("duplicar o monitor"))?;
    let quadro = quadro_atual(&mut captura)?.ok_or("o monitor não entregou quadro")?;
    png_da_textura(&dispositivo, &quadro.textura, captura.tamanho())
}

fn quadro_atual(captura: &mut CapturaDoMonitor) -> Result<Option<QuadroDoMonitor>, String> {
    for tentativa in 0..TENTATIVAS_NO_MONITOR {
        match captura.colher(ESPERA_POR_TENTATIVA, false).map_err(em("colher o quadro"))? {
            ColheitaDoMonitor::Quadro(quadro) if tentativa > 0 => return Ok(Some(quadro)),
            ColheitaDoMonitor::Perdido => return Err("acesso ao monitor perdido".into()),
            ColheitaDoMonitor::Quadro(_) | ColheitaDoMonitor::Nada => {}
        }
    }
    Ok(None)
}

fn png_da_textura(dispositivo: &Dispositivo, origem: &ID3D11Texture2D, tamanho: (u32, u32)) -> Result<Vec<u8>, String> {
    let (largura, altura) = tamanho_da_miniatura(tamanho);
    let pixels = reduzir(dispositivo, origem, tamanho, (largura, altura)).map_err(em("reduzir a janela"))?;
    png_de_bgrx(largura, altura, &pixels).map_err(em("gravar o PNG"))
}

fn reduzir(
    dispositivo: &Dispositivo,
    origem: &ID3D11Texture2D,
    tamanho: (u32, u32),
    (largura, altura): (u32, u32),
) -> windows::core::Result<Vec<u8>> {
    let destino = textura(&dispositivo.placa, largura, altura, DXGI_FORMAT_B8G8R8A8_UNORM, None)?;
    Encaixe::novo(dispositivo, &destino, tamanho, (largura, altura))?.encaixar(&dispositivo.contexto, origem)?;
    let leitura = textura_de_leitura(&dispositivo.placa, largura, altura, DXGI_FORMAT_B8G8R8A8_UNORM)?;
    let mut mapa = D3D11_MAPPED_SUBRESOURCE::default();
    unsafe {
        dispositivo.contexto.CopyResource(&leitura, &destino);
        dispositivo.contexto.Map(&leitura, 0, D3D11_MAP_READ, 0, Some(&mut mapa))?;
    }
    let pixels = copiar_linhas(&mapa, largura as usize * 4, altura as usize);
    unsafe { dispositivo.contexto.Unmap(&leitura, 0) };
    Ok(pixels)
}

fn tamanho_da_miniatura((largura, altura): (u32, u32)) -> (u32, u32) {
    let estreita = largura.clamp(1, LARGURA_DA_MINIATURA);
    let baixa = (u64::from(altura) * u64::from(estreita) / u64::from(largura.max(1))).max(1) as u32;
    (estreita, baixa)
}

#[cfg(test)]
mod testes {
    use super::*;
    use crate::tela::placas_de_hardware;
    use windows::Win32::Graphics::Direct3D11::D3D11_SUBRESOURCE_DATA;

    #[test]
    fn a_miniatura_tem_256_de_largura_e_a_proporcao_da_janela() {
        assert_eq!(tamanho_da_miniatura((1920, 1080)), (256, 144));
        assert_eq!(tamanho_da_miniatura((1003, 617)), (256, 157));
        assert_eq!(tamanho_da_miniatura((200, 150)), (200, 150));
        assert_eq!(tamanho_da_miniatura((4000, 1)), (256, 1));
    }

    #[test]
    #[ignore = "precisa de placa de vídeo"]
    fn a_textura_vira_um_png_do_tamanho_da_miniatura() {
        let _com = crate::com::ComDaThread::iniciar();
        let placa = placas_de_hardware().unwrap().into_iter().next().unwrap();
        let dispositivo = dispositivo_da_placa(&placa.adaptador).unwrap();
        let azul = 0xFF20_4080u32;
        let pixels = vec![azul; 1280 * 720];
        let dados = D3D11_SUBRESOURCE_DATA { pSysMem: pixels.as_ptr().cast(), SysMemPitch: 1280 * 4, SysMemSlicePitch: 0 };
        let origem = textura(&dispositivo.placa, 1280, 720, DXGI_FORMAT_B8G8R8A8_UNORM, Some(&dados)).unwrap();

        let reduzida = reduzir(&dispositivo, &origem, (1280, 720), (256, 144)).unwrap();
        assert_eq!(reduzida.len(), 256 * 144 * 4);
        let perto = |pixel: &[u8; 4]| pixel[..3].iter().zip([0x80u8, 0x40, 0x20]).all(|(&lido, esperado)| lido.abs_diff(esperado) <= 2);
        assert!(reduzida.as_chunks::<4>().0.iter().all(perto), "a cor passa intacta");

        let png = png_da_textura(&dispositivo, &origem, (1280, 720)).unwrap();
        assert_eq!(&png[..8], b"\x89PNG\r\n\x1a\n");
        assert_eq!(&png[12..16], b"IHDR");
        let medida = |i: usize| u32::from_be_bytes(png[i..i + 4].try_into().unwrap());
        assert_eq!((medida(16), medida(20)), (256, 144));
        assert_eq!((png[24], png[25]), (8, 2), "8 bits por canal, RGB sem alfa");
        assert_eq!(&png[png.len() - 8..png.len() - 4], b"IEND");
    }
}
