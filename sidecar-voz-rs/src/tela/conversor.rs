use super::dispositivo::Dispositivo;
use std::mem::ManuallyDrop;
use windows::Win32::Foundation::{E_POINTER, RECT};
use windows::Win32::Graphics::Direct3D11::{
    D3D11_TEX2D_VPIV, D3D11_TEX2D_VPOV, D3D11_VIDEO_COLOR, D3D11_VIDEO_COLOR_0, D3D11_VIDEO_COLOR_RGBA,
    D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE, D3D11_VIDEO_PROCESSOR_COLOR_SPACE, D3D11_VIDEO_PROCESSOR_CONTENT_DESC,
    D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC, D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC_0,
    D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC, D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC_0, D3D11_VIDEO_PROCESSOR_STREAM,
    D3D11_VIDEO_USAGE_PLAYBACK_NORMAL, D3D11_VPIV_DIMENSION_TEXTURE2D, D3D11_VPOV_DIMENSION_TEXTURE2D, ID3D11Texture2D,
    ID3D11VideoContext, ID3D11VideoDevice, ID3D11VideoProcessor, ID3D11VideoProcessorEnumerator,
    ID3D11VideoProcessorInputView, ID3D11VideoProcessorOutputView,
};
use windows::Win32::Graphics::Dxgi::Common::DXGI_RATIONAL;
use windows::core::Interface;

const RGB_DE_0_A_255: u32 = 0;
const BT709_DE_16_A_235: u32 = (1 << 2) | (1 << 4);
const PRETO: D3D11_VIDEO_COLOR = D3D11_VIDEO_COLOR {
    Anonymous: D3D11_VIDEO_COLOR_0 { RGBA: D3D11_VIDEO_COLOR_RGBA { R: 0.0, G: 0.0, B: 0.0, A: 1.0 } },
};

pub struct Conversor {
    contexto: ID3D11VideoContext,
    processador: ID3D11VideoProcessor,
    entrada: ID3D11VideoProcessorInputView,
    saida: ID3D11VideoProcessorOutputView,
}

impl Conversor {
    pub fn novo(
        dispositivo: &Dispositivo,
        origem: &ID3D11Texture2D,
        destino: &ID3D11Texture2D,
        de: (u32, u32),
        para: (u32, u32),
    ) -> windows::core::Result<Self> {
        let video: ID3D11VideoDevice = dispositivo.placa.cast()?;
        let contexto: ID3D11VideoContext = dispositivo.contexto.cast()?;
        let enumerador = unsafe { video.CreateVideoProcessorEnumerator(&conteudo(de, para))? };
        let processador = unsafe { video.CreateVideoProcessor(&enumerador, 0)? };
        let encaixado = encaixado(de, para);
        unsafe {
            contexto.VideoProcessorSetStreamFrameFormat(&processador, 0, D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE);
            contexto.VideoProcessorSetStreamAutoProcessingMode(&processador, 0, false);
            contexto.VideoProcessorSetStreamColorSpace(&processador, 0, &espaco(RGB_DE_0_A_255));
            contexto.VideoProcessorSetOutputColorSpace(&processador, &espaco(BT709_DE_16_A_235));
            contexto.VideoProcessorSetStreamDestRect(&processador, 0, true, Some(&raw const encaixado));
            contexto.VideoProcessorSetOutputBackgroundColor(&processador, false, &PRETO);
        }
        Ok(Self {
            entrada: vista_de_entrada(&video, &enumerador, origem)?,
            saida: vista_de_saida(&video, &enumerador, destino)?,
            contexto,
            processador,
        })
    }

    pub fn converter(&self) -> windows::core::Result<()> {
        let fluxo = D3D11_VIDEO_PROCESSOR_STREAM {
            Enable: true.into(),
            pInputSurface: ManuallyDrop::new(Some(self.entrada.clone())),
            ..Default::default()
        };
        let feito = unsafe {
            self.contexto.VideoProcessorBlt(&self.processador, &self.saida, 0, std::slice::from_ref(&fluxo))
        };
        drop(ManuallyDrop::into_inner(fluxo.pInputSurface));
        feito
    }
}

fn encaixado((origem_l, origem_a): (u32, u32), (destino_l, destino_a): (u32, u32)) -> RECT {
    let (origem_l, origem_a) = (u64::from(origem_l.max(1)), u64::from(origem_a.max(1)));
    let mais_larga = origem_l * u64::from(destino_a) > u64::from(destino_l) * origem_a;
    let (largura, altura) = if mais_larga {
        (destino_l, (origem_a * u64::from(destino_l) / origem_l) as u32)
    } else {
        ((origem_l * u64::from(destino_a) / origem_a) as u32, destino_a)
    };
    let (largura, altura) = (par(largura), par(altura));
    let (esquerda, topo) = (par((destino_l - largura) / 2), par((destino_a - altura) / 2));
    RECT {
        left: esquerda as i32,
        top: topo as i32,
        right: (esquerda + largura) as i32,
        bottom: (topo + altura) as i32,
    }
}

fn par(valor: u32) -> u32 {
    valor & !1
}

fn espaco(bits: u32) -> D3D11_VIDEO_PROCESSOR_COLOR_SPACE {
    D3D11_VIDEO_PROCESSOR_COLOR_SPACE { _bitfield: bits }
}

fn conteudo((origem_l, origem_a): (u32, u32), (destino_l, destino_a): (u32, u32)) -> D3D11_VIDEO_PROCESSOR_CONTENT_DESC {
    let ritmo = DXGI_RATIONAL { Numerator: 60, Denominator: 1 };
    D3D11_VIDEO_PROCESSOR_CONTENT_DESC {
        InputFrameFormat: D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE,
        InputFrameRate: ritmo,
        InputWidth: origem_l,
        InputHeight: origem_a,
        OutputFrameRate: ritmo,
        OutputWidth: destino_l,
        OutputHeight: destino_a,
        Usage: D3D11_VIDEO_USAGE_PLAYBACK_NORMAL,
    }
}

fn vista_de_entrada(
    video: &ID3D11VideoDevice,
    enumerador: &ID3D11VideoProcessorEnumerator,
    textura: &ID3D11Texture2D,
) -> windows::core::Result<ID3D11VideoProcessorInputView> {
    let desc = D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC {
        FourCC: 0,
        ViewDimension: D3D11_VPIV_DIMENSION_TEXTURE2D,
        Anonymous: D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC_0 { Texture2D: D3D11_TEX2D_VPIV { MipSlice: 0, ArraySlice: 0 } },
    };
    let mut vista = None;
    unsafe { video.CreateVideoProcessorInputView(textura, enumerador, &desc, Some(&mut vista))? };
    vista.ok_or_else(|| E_POINTER.into())
}

fn vista_de_saida(
    video: &ID3D11VideoDevice,
    enumerador: &ID3D11VideoProcessorEnumerator,
    textura: &ID3D11Texture2D,
) -> windows::core::Result<ID3D11VideoProcessorOutputView> {
    let desc = D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC {
        ViewDimension: D3D11_VPOV_DIMENSION_TEXTURE2D,
        Anonymous: D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC_0 { Texture2D: D3D11_TEX2D_VPOV { MipSlice: 0 } },
    };
    let mut vista = None;
    unsafe { video.CreateVideoProcessorOutputView(textura, enumerador, &desc, Some(&mut vista))? };
    vista.ok_or_else(|| E_POINTER.into())
}

#[cfg(test)]
mod testes {
    use super::*;

    fn caixa(retangulo: RECT) -> (i32, i32, i32, i32) {
        (retangulo.left, retangulo.top, retangulo.right, retangulo.bottom)
    }

    #[test]
    fn a_mesma_proporcao_ocupa_a_saida_inteira() {
        assert_eq!(caixa(encaixado((1920, 1080), (1280, 720))), (0, 0, 1280, 720));
        assert_eq!(caixa(encaixado((1280, 720), (1280, 720))), (0, 0, 1280, 720));
    }

    #[test]
    fn a_janela_mais_alta_ganha_faixas_dos_lados() {
        assert_eq!(caixa(encaixado((1000, 800), (1280, 720))), (190, 0, 1090, 720));
    }

    #[test]
    fn a_janela_mais_larga_ganha_faixas_em_cima_e_embaixo() {
        assert_eq!(caixa(encaixado((1600, 400), (1280, 720))), (0, 200, 1280, 520));
    }

    #[test]
    fn as_bordas_ficam_em_pixel_par() {
        assert_eq!(caixa(encaixado((642, 399), (640, 360))), (30, 0, 608, 360));
    }

    #[test]
    fn tamanho_zero_nao_divide_por_zero() {
        assert_eq!(caixa(encaixado((0, 0), (1280, 720))), (280, 0, 1000, 720));
    }
}
