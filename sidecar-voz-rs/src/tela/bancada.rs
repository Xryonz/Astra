use super::compressor::Entrada;
use super::conversor::Conversor;
use super::dispositivo::{Dispositivo, textura};
use super::ruido::Ruido;
use windows::Win32::Graphics::Direct3D11::{D3D11_BOX, ID3D11DeviceContext, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

#[derive(Clone, Copy)]
pub enum Origem {
    Sintetica,
    Capturada,
}

pub struct Bancada {
    contexto: ID3D11DeviceContext,
    entrada: ID3D11Texture2D,
    largura: u32,
    altura: u32,
    sintese: Option<Sintese>,
}

struct Sintese {
    ruido: Ruido,
    tela: ID3D11Texture2D,
    conversor: Option<Conversor>,
}

impl Bancada {
    pub fn montar(
        dispositivo: &Dispositivo,
        origem: Origem,
        entrada: Entrada,
        largura: u32,
        altura: u32,
    ) -> windows::core::Result<Self> {
        let destino = textura(&dispositivo.placa, largura, altura, entrada.formato_da_textura(), None)?;
        let sintese = match origem {
            Origem::Sintetica => Some(Sintese::montar(dispositivo, entrada, &destino, largura, altura)?),
            Origem::Capturada => None,
        };
        Ok(Self { contexto: dispositivo.contexto.clone(), entrada: destino, largura, altura, sintese })
    }

    pub fn entrada(&self) -> &ID3D11Texture2D {
        &self.entrada
    }

    pub fn pintar(&self, quadro: u32) {
        if let Some(sintese) = &self.sintese {
            sintese.ruido.pintar(&self.contexto, &sintese.tela, quadro);
        }
    }

    pub fn preparar(&self) -> windows::core::Result<()> {
        let Some(sintese) = &self.sintese else { return Ok(()) };
        match &sintese.conversor {
            Some(conversor) => conversor.converter(),
            None => {
                unsafe { self.contexto.CopyResource(&self.entrada, &sintese.tela) };
                Ok(())
            }
        }
    }

    pub fn trazer(&self, origem: &ID3D11Texture2D, (largura, altura): (u32, u32)) {
        let caixa = D3D11_BOX {
            left: 0,
            top: 0,
            front: 0,
            right: largura.min(self.largura),
            bottom: altura.min(self.altura),
            back: 1,
        };
        unsafe { self.contexto.CopySubresourceRegion(&self.entrada, 0, 0, 0, 0, origem, 0, Some(&caixa)) };
    }
}

impl Sintese {
    fn montar(
        dispositivo: &Dispositivo,
        entrada: Entrada,
        destino: &ID3D11Texture2D,
        largura: u32,
        altura: u32,
    ) -> windows::core::Result<Self> {
        let tela = textura(&dispositivo.placa, largura, altura, DXGI_FORMAT_B8G8R8A8_UNORM, None)?;
        let conversor = match entrada {
            Entrada::Nv12 => Some(Conversor::novo(dispositivo, &tela, destino, largura, altura)?),
            Entrada::Argb => None,
        };
        Ok(Self { ruido: Ruido::novo(&dispositivo.placa, largura, altura)?, tela, conversor })
    }
}
