use super::compressor::Entrada;
use super::conversor::Conversor;
use super::dispositivo::{Dispositivo, textura};
use super::ruido::Ruido;
use windows::Win32::Graphics::Direct3D11::{ID3D11DeviceContext, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

pub struct Bancada {
    contexto: ID3D11DeviceContext,
    ruido: Ruido,
    captura: ID3D11Texture2D,
    entrada: ID3D11Texture2D,
    conversor: Option<Conversor>,
}

impl Bancada {
    pub fn montar(dispositivo: &Dispositivo, entrada: Entrada, largura: u32, altura: u32) -> windows::core::Result<Self> {
        let captura = textura(&dispositivo.placa, largura, altura, DXGI_FORMAT_B8G8R8A8_UNORM, None)?;
        let destino = textura(&dispositivo.placa, largura, altura, entrada.formato_da_textura(), None)?;
        let conversor = match entrada {
            Entrada::Nv12 => Some(Conversor::novo(dispositivo, &captura, &destino, largura, altura)?),
            Entrada::Argb => None,
        };
        Ok(Self {
            contexto: dispositivo.contexto.clone(),
            ruido: Ruido::novo(&dispositivo.placa, largura, altura)?,
            captura,
            entrada: destino,
            conversor,
        })
    }

    pub fn entrada(&self) -> &ID3D11Texture2D {
        &self.entrada
    }

    pub fn pintar(&self, quadro: u32) {
        self.ruido.pintar(&self.contexto, &self.captura, quadro);
    }

    pub fn preparar(&self) -> windows::core::Result<()> {
        match &self.conversor {
            Some(conversor) => conversor.converter(),
            None => {
                unsafe { self.contexto.CopyResource(&self.entrada, &self.captura) };
                Ok(())
            }
        }
    }
}
