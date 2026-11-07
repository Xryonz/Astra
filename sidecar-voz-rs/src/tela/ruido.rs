use super::dispositivo::textura;
use windows::Win32::Graphics::Direct3D11::{D3D11_SUBRESOURCE_DATA, ID3D11Device, ID3D11DeviceContext, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

const IMAGENS: u32 = 8;
const TREMOR: u8 = 12;

pub struct Ruido {
    imagens: Vec<ID3D11Texture2D>,
}

impl Ruido {
    pub fn novo(placa: &ID3D11Device, largura: u32, altura: u32) -> windows::core::Result<Self> {
        let imagens = (0..IMAGENS)
            .map(|semente| {
                let pixels = degrade_com_tremor(largura as usize, altura as usize, semente);
                let dados = D3D11_SUBRESOURCE_DATA {
                    pSysMem: pixels.as_ptr().cast(),
                    SysMemPitch: largura * 4,
                    SysMemSlicePitch: 0,
                };
                textura(placa, largura, altura, DXGI_FORMAT_B8G8R8A8_UNORM, Some(&dados))
            })
            .collect::<windows::core::Result<_>>()?;
        Ok(Self { imagens })
    }

    pub fn pintar(&self, contexto: &ID3D11DeviceContext, destino: &ID3D11Texture2D, quadro: u32) {
        let imagem = &self.imagens[quadro as usize % self.imagens.len()];
        unsafe { contexto.CopyResource(destino, imagem) };
    }
}

fn degrade_com_tremor(largura: usize, altura: usize, semente: u32) -> Vec<u8> {
    let mut estado = 0x9E37_79B9_7F4A_7C15u64 ^ (u64::from(semente) << 32);
    let mut bytes = Vec::with_capacity(largura * altura * 4);
    for y in 0..altura {
        for x in 0..largura {
            estado ^= estado << 13;
            estado ^= estado >> 7;
            estado ^= estado << 17;
            let sorteio = estado.to_le_bytes();
            let base = [128, y * 255 / altura, x * 255 / largura];
            for (canal, valor) in base.into_iter().enumerate() {
                bytes.push(tremido(valor as u8, sorteio[canal]));
            }
            bytes.push(0xFF);
        }
    }
    bytes
}

fn tremido(valor: u8, sorteio: u8) -> u8 {
    (valor.saturating_add(sorteio % (2 * TREMOR + 1))).saturating_sub(TREMOR)
}
