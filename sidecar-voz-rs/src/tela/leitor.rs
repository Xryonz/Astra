use super::dispositivo::Dispositivo;
use windows::Win32::Foundation::E_POINTER;
use windows::Win32::Graphics::Direct3D11::{
    D3D11_BOX, D3D11_CPU_ACCESS_READ, D3D11_MAP_READ, D3D11_MAPPED_SUBRESOURCE, D3D11_TEXTURE2D_DESC,
    D3D11_USAGE_STAGING, ID3D11DeviceContext, ID3D11Texture2D,
};
use windows::Win32::Graphics::Dxgi::Common::{DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_SAMPLE_DESC};

pub struct LeitorDePixel {
    contexto: ID3D11DeviceContext,
    copia: ID3D11Texture2D,
}

impl LeitorDePixel {
    pub fn novo(dispositivo: &Dispositivo) -> windows::core::Result<Self> {
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

    pub fn ler(&self, textura: &ID3D11Texture2D, (x, y): (u32, u32)) -> windows::core::Result<u32> {
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
