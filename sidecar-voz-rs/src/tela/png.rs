use windows::Win32::Foundation::{E_POINTER, HGLOBAL};
use windows::Win32::Graphics::Imaging::{
    CLSID_WICImagingFactory, GUID_ContainerFormatPng, GUID_WICPixelFormat24bppBGR, GUID_WICPixelFormat32bppBGR,
    IWICBitmapFrameEncode, IWICImagingFactory, WICBitmapEncoderNoCache,
};
use windows::Win32::System::Com::StructuredStorage::{CreateStreamOnHGlobal, IPropertyBag2};
use windows::Win32::System::Com::{CLSCTX_INPROC_SERVER, CoCreateInstance, IStream, STREAM_SEEK_END, STREAM_SEEK_SET};

pub fn png_de_bgrx(largura: u32, altura: u32, pixels: &[u8]) -> windows::core::Result<Vec<u8>> {
    unsafe {
        let fabrica: IWICImagingFactory = CoCreateInstance(&CLSID_WICImagingFactory, None, CLSCTX_INPROC_SERVER)?;
        let imagem = fabrica.CreateBitmapFromMemory(largura, altura, &GUID_WICPixelFormat32bppBGR, largura * 4, pixels)?;
        let fluxo = CreateStreamOnHGlobal(HGLOBAL::default(), true)?;
        let gravador = fabrica.CreateEncoder(&GUID_ContainerFormatPng, std::ptr::null())?;
        gravador.Initialize(&fluxo, WICBitmapEncoderNoCache)?;
        let mut quadro: Option<IWICBitmapFrameEncode> = None;
        let mut opcoes: Option<IPropertyBag2> = None;
        gravador.CreateNewFrame(&mut quadro, &mut opcoes)?;
        let quadro = quadro.ok_or(E_POINTER)?;
        quadro.Initialize(opcoes.as_ref())?;
        quadro.SetSize(largura, altura)?;
        let mut formato = GUID_WICPixelFormat24bppBGR;
        quadro.SetPixelFormat(&mut formato)?;
        quadro.WriteSource(&imagem, std::ptr::null())?;
        quadro.Commit()?;
        gravador.Commit()?;
        ler_o_fluxo(&fluxo)
    }
}

fn ler_o_fluxo(fluxo: &IStream) -> windows::core::Result<Vec<u8>> {
    let mut tamanho = 0u64;
    unsafe {
        fluxo.Seek(0, STREAM_SEEK_END, Some(&mut tamanho))?;
        fluxo.Seek(0, STREAM_SEEK_SET, None)?;
    }
    let mut bytes = vec![0u8; tamanho as usize];
    let mut lidos = 0u32;
    unsafe { fluxo.Read(bytes.as_mut_ptr().cast(), tamanho as u32, Some(&mut lidos)).ok()? };
    bytes.truncate(lidos as usize);
    Ok(bytes)
}
