use super::bancada::{Bancada, Origem};
use super::codificadores::CodificadorDaPlaca;
use super::compressor::{Compressor, Configuracao, amostra_da_textura};
use super::dispositivo::{Dispositivo, dispositivo_da_placa};
use super::etapa::em;
use super::placas::Placa;
use windows::Win32::Graphics::Direct3D11::ID3D11Device;
use windows::Win32::Media::MediaFoundation::IMFSample;

pub struct Codificacao {
    pub compressor: Compressor,
    pub bancada: Bancada,
    pub amostra: IMFSample,
    pub nome: String,
    dispositivo: Dispositivo,
    _codificador: CodificadorDaPlaca,
}

impl Codificacao {
    pub fn placa(&self) -> &ID3D11Device {
        &self.dispositivo.placa
    }
}

pub fn abrir_codificacao(
    placa: &Placa,
    codificador: CodificadorDaPlaca,
    config: Configuracao,
    origem: Origem,
) -> Result<Codificacao, String> {
    let dispositivo = dispositivo_da_placa(&placa.adaptador).map_err(em("criar o dispositivo na placa"))?;
    let transformacao = codificador.ativar().map_err(em("ligar o codificador"))?;
    let compressor = Compressor::abrir(transformacao, &dispositivo.placa, config)?;
    let bancada = Bancada::montar(&dispositivo, origem, config.entrada, config.largura, config.altura)
        .map_err(em("montar a bancada"))?;
    let amostra = amostra_da_textura(bancada.entrada()).map_err(em("embrulhar a textura"))?;
    Ok(Codificacao {
        compressor,
        bancada,
        amostra,
        nome: codificador.nome.clone(),
        dispositivo,
        _codificador: codificador,
    })
}
