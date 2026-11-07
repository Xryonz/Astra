use super::dispositivo::Dispositivo;
use std::mem::ManuallyDrop;
use windows::Win32::Foundation::E_POINTER;
use windows::Win32::Graphics::Direct3D11::{
    D3D11_TEX2D_VPIV, D3D11_TEX2D_VPOV, D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE, D3D11_VIDEO_PROCESSOR_COLOR_SPACE,
    D3D11_VIDEO_PROCESSOR_CONTENT_DESC, D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC, D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC_0,
    D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC, D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC_0, D3D11_VIDEO_PROCESSOR_STREAM,
    D3D11_VIDEO_USAGE_PLAYBACK_NORMAL, D3D11_VPIV_DIMENSION_TEXTURE2D, D3D11_VPOV_DIMENSION_TEXTURE2D, ID3D11Texture2D,
    ID3D11VideoContext, ID3D11VideoDevice, ID3D11VideoProcessor, ID3D11VideoProcessorEnumerator,
    ID3D11VideoProcessorInputView, ID3D11VideoProcessorOutputView,
};
use windows::Win32::Graphics::Dxgi::Common::DXGI_RATIONAL;
use windows::core::Interface;

const RGB_DE_0_A_255: u32 = 0;
const BT709_DE_16_A_235: u32 = (1 << 2) | (1 << 4);

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
        largura: u32,
        altura: u32,
    ) -> windows::core::Result<Self> {
        let video: ID3D11VideoDevice = dispositivo.placa.cast()?;
        let contexto: ID3D11VideoContext = dispositivo.contexto.cast()?;
        let enumerador = unsafe { video.CreateVideoProcessorEnumerator(&conteudo(largura, altura))? };
        let processador = unsafe { video.CreateVideoProcessor(&enumerador, 0)? };
        unsafe {
            contexto.VideoProcessorSetStreamFrameFormat(&processador, 0, D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE);
            contexto.VideoProcessorSetStreamAutoProcessingMode(&processador, 0, false);
            contexto.VideoProcessorSetStreamColorSpace(&processador, 0, &espaco(RGB_DE_0_A_255));
            contexto.VideoProcessorSetOutputColorSpace(&processador, &espaco(BT709_DE_16_A_235));
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

fn espaco(bits: u32) -> D3D11_VIDEO_PROCESSOR_COLOR_SPACE {
    D3D11_VIDEO_PROCESSOR_COLOR_SPACE { _bitfield: bits }
}

fn conteudo(largura: u32, altura: u32) -> D3D11_VIDEO_PROCESSOR_CONTENT_DESC {
    let ritmo = DXGI_RATIONAL { Numerator: 60, Denominator: 1 };
    D3D11_VIDEO_PROCESSOR_CONTENT_DESC {
        InputFrameFormat: D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE,
        InputFrameRate: ritmo,
        InputWidth: largura,
        InputHeight: altura,
        OutputFrameRate: ritmo,
        OutputWidth: largura,
        OutputHeight: altura,
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
