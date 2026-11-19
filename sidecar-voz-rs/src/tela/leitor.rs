use super::dispositivo::{Dispositivo, textura_de_leitura};
use windows::Win32::Graphics::Direct3D11::{
    D3D11_BOX, D3D11_MAP_READ, D3D11_MAPPED_SUBRESOURCE, ID3D11DeviceContext, ID3D11Texture2D,
};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

pub struct LeitorDePixel {
    contexto: ID3D11DeviceContext,
    copia: ID3D11Texture2D,
}

impl LeitorDePixel {
    pub fn novo(dispositivo: &Dispositivo) -> windows::core::Result<Self> {
        let copia = textura_de_leitura(&dispositivo.placa, 1, 1, DXGI_FORMAT_B8G8R8A8_UNORM)?;
        Ok(Self { contexto: dispositivo.contexto.clone(), copia })
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
