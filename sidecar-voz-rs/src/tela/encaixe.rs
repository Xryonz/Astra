use super::conversor::Conversor;
use super::dispositivo::{Dispositivo, textura};
use windows::Win32::Graphics::Direct3D11::{ID3D11DeviceContext, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

pub struct Encaixe {
    tamanho: (u32, u32),
    copia: ID3D11Texture2D,
    conversor: Conversor,
}

impl Encaixe {
    pub fn novo(
        dispositivo: &Dispositivo,
        destino: &ID3D11Texture2D,
        tamanho: (u32, u32),
        saida: (u32, u32),
    ) -> windows::core::Result<Self> {
        let copia = textura(&dispositivo.placa, tamanho.0, tamanho.1, DXGI_FORMAT_B8G8R8A8_UNORM, None)?;
        let conversor = Conversor::novo(dispositivo, &copia, destino, tamanho, saida)?;
        Ok(Self { tamanho, copia, conversor })
    }

    pub fn serve_para(&self, tamanho: (u32, u32)) -> bool {
        self.tamanho == tamanho
    }

    pub fn copia(&self) -> (&ID3D11Texture2D, (u32, u32)) {
        (&self.copia, self.tamanho)
    }

    pub fn encaixar(&self, contexto: &ID3D11DeviceContext, origem: &ID3D11Texture2D) -> windows::core::Result<()> {
        unsafe { contexto.CopyResource(&self.copia, origem) };
        self.conversor.converter()
    }
}
