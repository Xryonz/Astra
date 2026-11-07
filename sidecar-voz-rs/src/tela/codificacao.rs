use super::bancada::{Bancada, Origem};
use super::codificadores::Codificador;
use super::compressor::{Compressor, Configuracao, amostra_da_textura};
use super::dispositivo::{Dispositivo, dispositivo_da_placa};
use super::etapa::em;
use super::placas::Placa;
use windows::Win32::Graphics::Direct3D11::ID3D11Device;
use windows::Win32::Media::MediaFoundation::IMFSample;

const ALTURA_MAXIMA_SEM_PLACA: u32 = 720;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Caminho {
    Placa,
    Memoria,
}

pub fn teto_sem_placa(largura: u32, altura: u32) -> (u32, u32) {
    if altura <= ALTURA_MAXIMA_SEM_PLACA {
        return (largura, altura);
    }
    let largura = (u64::from(largura) * u64::from(ALTURA_MAXIMA_SEM_PLACA) / u64::from(altura)) as u32;
    (largura & !1, ALTURA_MAXIMA_SEM_PLACA)
}

pub struct Codificacao {
    pub compressor: Compressor,
    pub bancada: Bancada,
    pub amostra: IMFSample,
    pub nome: String,
    pub caminho: Caminho,
    pub saida: (u32, u32),
    dispositivo: Dispositivo,
    _codificador: Codificador,
}

impl Codificacao {
    pub fn placa(&self) -> &ID3D11Device {
        &self.dispositivo.placa
    }

    pub fn dispositivo(&self) -> &Dispositivo {
        &self.dispositivo
    }
}

pub fn abrir_codificacao(
    placa: &Placa,
    codificador: Codificador,
    config: Configuracao,
    origem: Origem,
    caminho: Caminho,
) -> Result<Codificacao, String> {
    let dispositivo = dispositivo_da_placa(&placa.adaptador).map_err(em("criar o dispositivo na placa"))?;
    let transformacao = codificador.ativar().map_err(em("ligar o codificador"))?;
    let na_placa = (caminho == Caminho::Placa).then_some(&dispositivo.placa);
    let compressor = Compressor::abrir(transformacao, na_placa, config)?;
    let bancada = Bancada::montar(&dispositivo, origem, config.entrada, config.largura, config.altura)
        .map_err(em("montar a bancada"))?;
    let amostra = amostra_da_textura(bancada.entrada()).map_err(em("embrulhar a textura"))?;
    Ok(Codificacao {
        compressor,
        bancada,
        amostra,
        nome: codificador.nome.clone(),
        caminho,
        saida: (config.largura, config.altura),
        dispositivo,
        _codificador: codificador,
    })
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn sem_placa_o_teto_e_720p_com_a_mesma_proporcao() {
        assert_eq!(teto_sem_placa(1920, 1080), (1280, 720));
        assert_eq!(teto_sem_placa(1280, 720), (1280, 720));
        assert_eq!(teto_sem_placa(854, 480), (854, 480));
        assert_eq!(teto_sem_placa(2560, 1440), (1280, 720));
        assert_eq!(teto_sem_placa(3440, 1440), (1720, 720));
    }
}
