use windows::Win32::Foundation::{E_POINTER, HMODULE};
use windows::Win32::Graphics::Direct3D::D3D_DRIVER_TYPE_UNKNOWN;
use windows::Win32::Graphics::Direct3D11::{
    D3D11_BIND_RENDER_TARGET, D3D11_BIND_SHADER_RESOURCE, D3D11_BIND_VIDEO_ENCODER, D3D11_CPU_ACCESS_READ,
    D3D11_CREATE_DEVICE_BGRA_SUPPORT, D3D11_CREATE_DEVICE_VIDEO_SUPPORT, D3D11_FORMAT_SUPPORT_RENDER_TARGET,
    D3D11_FORMAT_SUPPORT_SHADER_SAMPLE, D3D11_FORMAT_SUPPORT_VIDEO_ENCODER, D3D11_MAPPED_SUBRESOURCE, D3D11_SDK_VERSION,
    D3D11_SUBRESOURCE_DATA, D3D11_TEXTURE2D_DESC, D3D11_USAGE_DEFAULT, D3D11_USAGE_STAGING, D3D11CreateDevice,
    ID3D11Device, ID3D11DeviceContext, ID3D11Multithread, ID3D11Texture2D,
};
use windows::Win32::Graphics::Dxgi::Common::{DXGI_FORMAT, DXGI_SAMPLE_DESC};
use windows::Win32::Graphics::Dxgi::IDXGIAdapter1;
use windows::core::Interface;

#[derive(Clone)]
pub struct Dispositivo {
    pub placa: ID3D11Device,
    pub contexto: ID3D11DeviceContext,
}

pub fn dispositivo_da_placa(adaptador: &IDXGIAdapter1) -> windows::core::Result<Dispositivo> {
    let mut placa = None;
    let mut contexto = None;
    unsafe {
        D3D11CreateDevice(
            adaptador,
            D3D_DRIVER_TYPE_UNKNOWN,
            HMODULE::default(),
            D3D11_CREATE_DEVICE_BGRA_SUPPORT | D3D11_CREATE_DEVICE_VIDEO_SUPPORT,
            None,
            D3D11_SDK_VERSION,
            Some(&mut placa),
            None,
            Some(&mut contexto),
        )?;
    }
    let placa: ID3D11Device = placa.ok_or(E_POINTER)?;
    let _ = unsafe { placa.cast::<ID3D11Multithread>()?.SetMultithreadProtected(true) };
    Ok(Dispositivo { placa, contexto: contexto.ok_or(E_POINTER)? })
}

pub fn textura(
    placa: &ID3D11Device,
    largura: u32,
    altura: u32,
    formato: DXGI_FORMAT,
    pixels: Option<&D3D11_SUBRESOURCE_DATA>,
) -> windows::core::Result<ID3D11Texture2D> {
    let desc = D3D11_TEXTURE2D_DESC {
        Width: largura,
        Height: altura,
        MipLevels: 1,
        ArraySize: 1,
        Format: formato,
        SampleDesc: DXGI_SAMPLE_DESC { Count: 1, Quality: 0 },
        Usage: D3D11_USAGE_DEFAULT,
        BindFlags: amarracoes_aceitas(placa, formato),
        CPUAccessFlags: 0,
        MiscFlags: 0,
    };
    let mut textura = None;
    unsafe { placa.CreateTexture2D(&desc, pixels.map(|dados| dados as *const _), Some(&mut textura))? };
    textura.ok_or_else(|| E_POINTER.into())
}

pub fn textura_de_leitura(
    placa: &ID3D11Device,
    largura: u32,
    altura: u32,
    formato: DXGI_FORMAT,
) -> windows::core::Result<ID3D11Texture2D> {
    let desc = D3D11_TEXTURE2D_DESC {
        Width: largura,
        Height: altura,
        MipLevels: 1,
        ArraySize: 1,
        Format: formato,
        SampleDesc: DXGI_SAMPLE_DESC { Count: 1, Quality: 0 },
        Usage: D3D11_USAGE_STAGING,
        BindFlags: 0,
        CPUAccessFlags: D3D11_CPU_ACCESS_READ.0 as u32,
        MiscFlags: 0,
    };
    let mut textura = None;
    unsafe { placa.CreateTexture2D(&desc, None, Some(&mut textura))? };
    textura.ok_or_else(|| E_POINTER.into())
}

pub fn copiar_linhas(mapa: &D3D11_MAPPED_SUBRESOURCE, bytes_por_linha: usize, linhas: usize) -> Vec<u8> {
    let passo = mapa.RowPitch as usize;
    let origem = unsafe { std::slice::from_raw_parts(mapa.pData as *const u8, passo * linhas) };
    origem.chunks(passo).flat_map(|linha| &linha[..bytes_por_linha]).copied().collect()
}

fn amarracoes_aceitas(placa: &ID3D11Device, formato: DXGI_FORMAT) -> u32 {
    let aceita = unsafe { placa.CheckFormatSupport(formato) }.unwrap_or_default();
    [
        (D3D11_FORMAT_SUPPORT_SHADER_SAMPLE.0, D3D11_BIND_SHADER_RESOURCE.0),
        (D3D11_FORMAT_SUPPORT_RENDER_TARGET.0, D3D11_BIND_RENDER_TARGET.0),
        (D3D11_FORMAT_SUPPORT_VIDEO_ENCODER.0, D3D11_BIND_VIDEO_ENCODER.0),
    ]
    .into_iter()
    .filter(|&(suporte, _)| aceita & suporte as u32 != 0)
    .fold(0, |amarracoes, (_, amarracao)| amarracoes | amarracao as u32)
}
