use super::compressor::Entrada;
use super::conversor::Conversor;
use super::dispositivo::{Dispositivo, textura};
use super::encaixe::Encaixe;
use super::ruido::Ruido;
use windows::Win32::Graphics::Direct3D11::ID3D11Texture2D;
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

#[derive(Clone, Copy)]
pub enum Origem {
    Sintetica,
    Capturada,
}

pub struct Bancada {
    dispositivo: Dispositivo,
    entrada: ID3D11Texture2D,
    formato: Entrada,
    largura: u32,
    altura: u32,
    sintese: Option<Sintese>,
    encaixe: Option<Encaixe>,
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
        Ok(Self {
            dispositivo: dispositivo.clone(),
            entrada: destino,
            formato: entrada,
            largura,
            altura,
            sintese,
            encaixe: None,
        })
    }

    pub fn entrada(&self) -> &ID3D11Texture2D {
        &self.entrada
    }

    pub fn pintar(&self, quadro: u32) {
        if let Some(sintese) = &self.sintese {
            sintese.ruido.pintar(&self.dispositivo.contexto, &sintese.tela, quadro);
        }
    }

    pub fn preparar(&self) -> windows::core::Result<()> {
        let Some(sintese) = &self.sintese else { return Ok(()) };
        match &sintese.conversor {
            Some(conversor) => conversor.converter(),
            None => {
                unsafe { self.dispositivo.contexto.CopyResource(&self.entrada, &sintese.tela) };
                Ok(())
            }
        }
    }

    pub fn fonte_do_espelho(&self) -> (&ID3D11Texture2D, (u32, u32)) {
        let tamanho = (self.largura, self.altura);
        match (&self.encaixe, &self.sintese) {
            (Some(encaixe), _) => encaixe.copia(),
            (None, Some(sintese)) => (&sintese.tela, tamanho),
            (None, None) => (&self.entrada, tamanho),
        }
    }

    pub fn trazer(&mut self, origem: &ID3D11Texture2D, tamanho: (u32, u32)) -> windows::core::Result<()> {
        if tamanho == (self.largura, self.altura) && matches!(self.formato, Entrada::Argb) {
            self.encaixe = None;
            unsafe { self.dispositivo.contexto.CopyResource(&self.entrada, origem) };
            return Ok(());
        }
        let encaixe = match self.encaixe.take() {
            Some(encaixe) if encaixe.serve_para(tamanho) => encaixe,
            _ => Encaixe::novo(&self.dispositivo, &self.entrada, tamanho, (self.largura, self.altura))?,
        };
        let feito = encaixe.encaixar(&self.dispositivo.contexto, origem);
        self.encaixe = Some(encaixe);
        feito
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
        let tamanho = (largura, altura);
        let conversor = match entrada {
            Entrada::Nv12 => Some(Conversor::novo(dispositivo, &tela, destino, tamanho, tamanho)?),
            Entrada::Argb => None,
        };
        Ok(Self { ruido: Ruido::novo(&dispositivo.placa, largura, altura)?, tela, conversor })
    }
}

#[cfg(test)]
mod testes {
    use super::*;
    use crate::tela::{LeitorDePixel, dispositivo_da_placa, placas_de_hardware};
    use windows::Win32::Graphics::Direct3D11::D3D11_SUBRESOURCE_DATA;

    fn imagem_branca(dispositivo: &Dispositivo, (largura, altura): (u32, u32)) -> ID3D11Texture2D {
        let pixels = vec![u32::MAX; (largura * altura) as usize];
        let dados = D3D11_SUBRESOURCE_DATA { pSysMem: pixels.as_ptr().cast(), SysMemPitch: largura * 4, SysMemSlicePitch: 0 };
        textura(&dispositivo.placa, largura, altura, DXGI_FORMAT_B8G8R8A8_UNORM, Some(&dados)).unwrap()
    }

    fn canais(cor: u32) -> [u8; 3] {
        let [azul, verde, vermelho, _] = cor.to_le_bytes();
        [vermelho, verde, azul]
    }

    fn e_branco(leitor: &LeitorDePixel, bancada: &Bancada, ponto: (u32, u32)) -> bool {
        canais(leitor.ler(bancada.entrada(), ponto).unwrap()).iter().all(|&canal| canal >= 0xF0)
    }

    fn e_preto(leitor: &LeitorDePixel, bancada: &Bancada, ponto: (u32, u32)) -> bool {
        canais(leitor.ler(bancada.entrada(), ponto).unwrap()).iter().all(|&canal| canal <= 0x10)
    }

    #[test]
    #[ignore = "precisa de placa de vídeo"]
    fn a_janela_de_outro_tamanho_entra_inteira_com_faixas_pretas() {
        let placa = placas_de_hardware().unwrap().into_iter().next().unwrap();
        let dispositivo = dispositivo_da_placa(&placa.adaptador).unwrap();
        let leitor = LeitorDePixel::novo(&dispositivo).unwrap();
        let mut bancada = Bancada::montar(&dispositivo, Origem::Capturada, Entrada::Argb, 1280, 720).unwrap();

        bancada.trazer(&imagem_branca(&dispositivo, (1000, 800)), (1000, 800)).unwrap();
        assert!(e_preto(&leitor, &bancada, (100, 360)), "faixa da esquerda");
        assert!(e_branco(&leitor, &bancada, (192, 360)), "começo da janela");
        assert!(e_branco(&leitor, &bancada, (640, 360)), "meio da janela");
        assert!(e_branco(&leitor, &bancada, (1087, 360)), "fim da janela");
        assert!(e_preto(&leitor, &bancada, (1180, 360)), "faixa da direita");

        bancada.trazer(&imagem_branca(&dispositivo, (1600, 400)), (1600, 400)).unwrap();
        assert!(e_preto(&leitor, &bancada, (640, 100)), "faixa de cima");
        assert!(e_branco(&leitor, &bancada, (8, 360)), "a janela mais larga encosta nos lados");
        assert!(e_preto(&leitor, &bancada, (640, 620)), "faixa de baixo");

        bancada.trazer(&imagem_branca(&dispositivo, (1280, 720)), (1280, 720)).unwrap();
        assert!(e_branco(&leitor, &bancada, (640, 100)), "do mesmo tamanho, a cópia cobre tudo");
        assert!(e_branco(&leitor, &bancada, (2, 2)), "canto de cima");
    }
}
