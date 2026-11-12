use super::conversor::Conversor;
use super::dispositivo::{Dispositivo, preencher_linhas, textura, textura_de_leitura};
use crate::entrega::Quadro;
use std::time::{Duration, Instant};
use windows::Win32::Graphics::Direct3D11::{D3D11_MAP_READ, D3D11_MAPPED_SUBRESOURCE, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_NV12;
use windows::core::Interface;

const LARGURA_DO_ESPELHO: u32 = 320;
const COMPASSO_DO_ESPELHO: Duration = Duration::from_millis(125);

pub struct Espelho {
    dispositivo: Dispositivo,
    montado: Option<Montado>,
    proximo: Instant,
}

struct Montado {
    fonte: usize,
    tamanho: (u32, u32),
    largura: u32,
    altura: u32,
    destino: ID3D11Texture2D,
    leitura: ID3D11Texture2D,
    conversor: Conversor,
}

impl Espelho {
    pub fn novo(dispositivo: &Dispositivo) -> Self {
        Self { dispositivo: dispositivo.clone(), montado: None, proximo: Instant::now() }
    }

    pub fn talvez(
        &mut self,
        fonte: &ID3D11Texture2D,
        tamanho: (u32, u32),
        cheio: bool,
        largura_da_saida: u32,
        reservar: impl FnOnce(usize) -> Vec<u8>,
    ) -> windows::core::Result<Option<Quadro>> {
        let agora = Instant::now();
        if !cheio && agora < self.proximo {
            return Ok(None);
        }
        self.proximo = agora + COMPASSO_DO_ESPELHO;
        let limite = if cheio { largura_da_saida } else { LARGURA_DO_ESPELHO };
        let Some(alvo) = tamanho_do_espelho(tamanho, limite) else { return Ok(None) };
        let identidade = fonte.as_raw() as usize;
        if !self.montado.as_ref().is_some_and(|montado| montado.serve_para(identidade, tamanho, alvo)) {
            self.montado = None;
            self.montado = Some(Montado::novo(&self.dispositivo, fonte, tamanho, alvo)?);
        }
        match &self.montado {
            Some(montado) => montado.ler(&self.dispositivo, reservar).map(Some),
            None => Ok(None),
        }
    }
}

impl Montado {
    fn novo(
        dispositivo: &Dispositivo,
        fonte: &ID3D11Texture2D,
        tamanho: (u32, u32),
        (largura, altura): (u32, u32),
    ) -> windows::core::Result<Self> {
        let destino = textura(&dispositivo.placa, largura, altura, DXGI_FORMAT_NV12, None)?;
        let leitura = textura_de_leitura(&dispositivo.placa, largura, altura, DXGI_FORMAT_NV12)?;
        let conversor = Conversor::novo(dispositivo, fonte, &destino, tamanho, (largura, altura))?;
        let fonte = fonte.as_raw() as usize;
        Ok(Self { fonte, tamanho, largura, altura, destino, leitura, conversor })
    }

    fn serve_para(&self, fonte: usize, tamanho: (u32, u32), alvo: (u32, u32)) -> bool {
        self.fonte == fonte && self.tamanho == tamanho && (self.largura, self.altura) == alvo
    }

    fn ler(&self, dispositivo: &Dispositivo, reservar: impl FnOnce(usize) -> Vec<u8>) -> windows::core::Result<Quadro> {
        self.conversor.converter()?;
        let contexto = &dispositivo.contexto;
        let mut mapa = D3D11_MAPPED_SUBRESOURCE::default();
        unsafe {
            contexto.CopyResource(&self.leitura, &self.destino);
            contexto.Map(&self.leitura, 0, D3D11_MAP_READ, 0, Some(&mut mapa))?;
        }
        let mut dados = reservar(self.largura as usize * self.altura as usize * 3 / 2);
        preencher_linhas(&mapa, self.largura as usize, &mut dados);
        unsafe { contexto.Unmap(&self.leitura, 0) };
        Ok(Quadro { dados, largura: self.largura, altura: self.altura, passo: self.largura })
    }
}

fn tamanho_do_espelho((largura, altura): (u32, u32), limite: u32) -> Option<(u32, u32)> {
    let estreita = largura.min(limite);
    let baixa = (u64::from(altura) * u64::from(estreita) / u64::from(largura.max(1))) as u32;
    let (estreita, baixa) = (estreita & !1, baixa & !1);
    (estreita > 0 && baixa > 0).then_some((estreita, baixa))
}

#[cfg(test)]
mod testes {
    use super::*;
    use crate::tela::{dispositivo_da_placa, placas_de_hardware};
    use windows::Win32::Graphics::Direct3D11::D3D11_SUBRESOURCE_DATA;
    use windows::Win32::Graphics::Dxgi::Common::DXGI_FORMAT_B8G8R8A8_UNORM;

    #[test]
    fn o_espelho_tem_320_de_largura_e_a_proporcao_da_fonte() {
        assert_eq!(tamanho_do_espelho((1920, 1080), LARGURA_DO_ESPELHO), Some((320, 180)));
        assert_eq!(tamanho_do_espelho((1000, 800), LARGURA_DO_ESPELHO), Some((320, 256)));
        assert_eq!(tamanho_do_espelho((200, 100), LARGURA_DO_ESPELHO), Some((200, 100)));
        assert_eq!(tamanho_do_espelho((321, 181), LARGURA_DO_ESPELHO), Some((320, 180)));
        assert_eq!(tamanho_do_espelho((1, 1), LARGURA_DO_ESPELHO), None);
    }

    #[test]
    fn no_palco_o_espelho_tem_a_largura_da_transmissao_sem_ampliar() {
        assert_eq!(tamanho_do_espelho((2560, 1440), 1920), Some((1920, 1080)));
        assert_eq!(tamanho_do_espelho((1280, 720), 1920), Some((1280, 720)));
        assert_eq!(tamanho_do_espelho((1001, 501), 1920), Some((1000, 500)));
    }

    #[test]
    #[ignore = "precisa de placa de vídeo"]
    fn a_imagem_branca_sai_em_nv12_branco_no_tamanho_do_espelho() {
        let placa = placas_de_hardware().unwrap().into_iter().next().unwrap();
        let dispositivo = dispositivo_da_placa(&placa.adaptador).unwrap();
        let pixels = vec![u32::MAX; 1280 * 720];
        let dados = D3D11_SUBRESOURCE_DATA { pSysMem: pixels.as_ptr().cast(), SysMemPitch: 1280 * 4, SysMemSlicePitch: 0 };
        let fonte = textura(&dispositivo.placa, 1280, 720, DXGI_FORMAT_B8G8R8A8_UNORM, Some(&dados)).unwrap();
        let mut espelho = Espelho::novo(&dispositivo);
        let novo = |tamanho| vec![0; tamanho];

        let quadro = espelho.talvez(&fonte, (1280, 720), false, 1280, novo).unwrap().expect("o primeiro quadro sai na hora");
        assert_eq!((quadro.largura, quadro.altura, quadro.passo), (320, 180, 320));
        assert_eq!(quadro.dados.len(), 320 * 180 * 3 / 2);
        let (luz, cor) = quadro.dados.split_at(320 * 180);
        assert!(luz.iter().all(|&y| (230..=240).contains(&y)), "branco em BT.709 limitado é 235");
        assert!(cor.iter().all(|&c| (124..=132).contains(&c)), "sem cor, o croma fica em 128");
        assert!(espelho.talvez(&fonte, (1280, 720), false, 1280, novo).unwrap().is_none(), "o próximo só depois de 125 ms");

        let cheio = espelho.talvez(&fonte, (1280, 720), true, 1280, novo).unwrap().expect("no palco não espera os 125 ms");
        assert_eq!((cheio.largura, cheio.altura, cheio.dados.len()), (1280, 720, 1280 * 720 * 3 / 2));
    }
}
