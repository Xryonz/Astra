mod comandos;
mod saida;
mod tipos;

pub use comandos::Aceitos;

use super::etapa::em;
use comandos::Comandos;
use saida::{Puxado, puxar};
use std::time::{Duration, Instant};
use windows::Win32::Foundation::E_POINTER;
use windows::Win32::Graphics::Direct3D11::{ID3D11Device, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::Common::{DXGI_FORMAT, DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_FORMAT_NV12};
use windows::Win32::Media::MediaFoundation::{
    ICodecAPI, IMF2DBuffer, IMFDXGIDeviceManager, IMFMediaEventGenerator, IMFSample, IMFTransform,
    METransformDrainComplete, METransformHaveOutput, METransformNeedInput, MF_EVENT_FLAG_NONE,
    MF_TRANSFORM_ASYNC_UNLOCK, MFCreateDXGIDeviceManager, MFCreateDXGISurfaceBuffer, MFCreateSample,
    MFT_MESSAGE_COMMAND_DRAIN, MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, MFT_MESSAGE_NOTIFY_END_OF_STREAM,
    MFT_MESSAGE_NOTIFY_END_STREAMING, MFT_MESSAGE_NOTIFY_START_OF_STREAM, MFT_MESSAGE_SET_D3D_MANAGER,
    MFT_MESSAGE_TYPE, MFT_OUTPUT_STREAM_PROVIDES_SAMPLES, MFVideoFormat_ARGB32, MFVideoFormat_NV12,
};
use windows::core::{GUID, Interface};

const CEM_NANOS_POR_SEGUNDO: i64 = 10_000_000;
const PEDE_ENTRADA: u32 = METransformNeedInput.0 as u32;
const TEM_SAIDA: u32 = METransformHaveOutput.0 as u32;
const FIM_DO_DRENO: u32 = METransformDrainComplete.0 as u32;

#[derive(Clone, Copy)]
pub enum Entrada {
    Argb,
    Nv12,
}

impl Entrada {
    pub fn nome(self) -> &'static str {
        match self {
            Self::Argb => "argb",
            Self::Nv12 => "nv12",
        }
    }

    pub fn formato_da_textura(self) -> DXGI_FORMAT {
        match self {
            Self::Argb => DXGI_FORMAT_B8G8R8A8_UNORM,
            Self::Nv12 => DXGI_FORMAT_NV12,
        }
    }

    fn subtipo(self) -> GUID {
        match self {
            Self::Argb => MFVideoFormat_ARGB32,
            Self::Nv12 => MFVideoFormat_NV12,
        }
    }
}

#[derive(Clone, Copy)]
pub struct Configuracao {
    pub largura: u32,
    pub altura: u32,
    pub fps: u32,
    pub kbps: u32,
    pub entrada: Entrada,
}

pub enum Evento {
    PedeEntrada,
    Saida { tempo: Option<i64>, bytes: Vec<u8>, chegou: Instant, custo: Duration },
    FimDoDreno,
    Nenhum,
}

pub struct Compressor {
    transformacao: IMFTransform,
    eventos: IMFMediaEventGenerator,
    comandos: Comandos,
    _gerente: IMFDXGIDeviceManager,
    config: Configuracao,
    perfil: u32,
    traz_amostra: bool,
    tamanho_da_saida: u32,
    pub aceitos: Aceitos,
}

impl Compressor {
    pub fn abrir(transformacao: IMFTransform, placa: &ID3D11Device, config: Configuracao) -> Result<Self, String> {
        destrancar(&transformacao).map_err(em("destrancar o modo assíncrono"))?;
        let gerente = gerente_da_placa(placa).map_err(em("criar o gerente da placa"))?;
        unsafe { transformacao.ProcessMessage(MFT_MESSAGE_SET_D3D_MANAGER, gerente.as_raw() as usize) }
            .map_err(em("entregar a placa ao codificador"))?;
        let comandos = Comandos(transformacao.cast::<ICodecAPI>().map_err(em("pedir o ICodecAPI"))?);
        let aceitos = comandos.configurar(config.kbps);
        let perfil = tipos::configurar_saida(&transformacao, &config)?;
        tipos::configurar_entrada(&transformacao, &config)?;
        let eventos = transformacao.cast::<IMFMediaEventGenerator>().map_err(em("pedir os eventos"))?;
        let mut compressor = Self {
            transformacao,
            eventos,
            comandos,
            _gerente: gerente,
            config,
            perfil,
            traz_amostra: false,
            tamanho_da_saida: 0,
            aceitos,
        };
        compressor.ler_a_saida().map_err(em("perguntar como sai o H.264"))?;
        compressor.avisar(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING).map_err(em("começar o fluxo"))?;
        compressor.avisar(MFT_MESSAGE_NOTIFY_START_OF_STREAM).map_err(em("abrir o fluxo"))?;
        Ok(compressor)
    }

    pub fn perfil_pedido(&self) -> u32 {
        self.perfil
    }

    pub fn entregar(&self, amostra: &IMFSample, quadro: u32) -> windows::core::Result<()> {
        let fps = i64::from(self.config.fps);
        unsafe {
            amostra.SetSampleTime(i64::from(quadro) * CEM_NANOS_POR_SEGUNDO / fps)?;
            amostra.SetSampleDuration(CEM_NANOS_POR_SEGUNDO / fps)?;
            self.transformacao.ProcessInput(0, amostra, 0)
        }
    }

    pub fn quadro_do_tempo(&self, tempo: i64) -> u32 {
        ((tempo * i64::from(self.config.fps) + CEM_NANOS_POR_SEGUNDO / 2) / CEM_NANOS_POR_SEGUNDO) as u32
    }

    pub fn proximo_evento(&mut self) -> Result<Evento, String> {
        let evento = unsafe { self.eventos.GetEvent(MF_EVENT_FLAG_NONE) }.map_err(em("esperar o codificador"))?;
        let chegou = Instant::now();
        unsafe { evento.GetStatus() }
            .and_then(|estado| estado.ok())
            .map_err(em("o codificador avisou uma falha"))?;
        match unsafe { evento.GetType() }.map_err(em("ler o evento do codificador"))? {
            PEDE_ENTRADA => Ok(Evento::PedeEntrada),
            TEM_SAIDA => self.colher(chegou),
            FIM_DO_DRENO => Ok(Evento::FimDoDreno),
            _ => Ok(Evento::Nenhum),
        }
    }

    pub fn mudar_taxa(&mut self, kbps: u32) -> bool {
        let aceita = self.comandos.mudar_taxa(kbps);
        if aceita {
            self.config.kbps = kbps;
        }
        aceita
    }

    pub fn pedir_quadro_chave(&self) -> bool {
        self.comandos.pedir_quadro_chave()
    }

    pub fn drenar(&self) -> windows::core::Result<()> {
        self.avisar(MFT_MESSAGE_NOTIFY_END_OF_STREAM)?;
        self.avisar(MFT_MESSAGE_COMMAND_DRAIN)
    }

    fn colher(&mut self, chegou: Instant) -> Result<Evento, String> {
        match puxar(&self.transformacao, self.traz_amostra, self.tamanho_da_saida).map_err(em("puxar o H.264"))? {
            Puxado::Pacote { tempo, bytes } => Ok(Evento::Saida { tempo, bytes, chegou, custo: chegou.elapsed() }),
            Puxado::Nada => Ok(Evento::Nenhum),
            Puxado::MudouOFormato => {
                tipos::saida_com_perfil(&self.transformacao, &self.config, self.perfil)
                    .map_err(em("refazer a saída H.264"))?;
                self.ler_a_saida().map_err(em("perguntar como sai o H.264"))?;
                Ok(Evento::Nenhum)
            }
        }
    }

    fn ler_a_saida(&mut self) -> windows::core::Result<()> {
        let info = unsafe { self.transformacao.GetOutputStreamInfo(0)? };
        self.traz_amostra = info.dwFlags & MFT_OUTPUT_STREAM_PROVIDES_SAMPLES.0 as u32 != 0;
        self.tamanho_da_saida = info.cbSize;
        Ok(())
    }

    fn avisar(&self, recado: MFT_MESSAGE_TYPE) -> windows::core::Result<()> {
        unsafe { self.transformacao.ProcessMessage(recado, 0) }
    }
}

impl Drop for Compressor {
    fn drop(&mut self) {
        let _ = self.avisar(MFT_MESSAGE_NOTIFY_END_STREAMING);
    }
}

pub fn amostra_da_textura(textura: &ID3D11Texture2D) -> windows::core::Result<IMFSample> {
    unsafe {
        let buffer = MFCreateDXGISurfaceBuffer(&ID3D11Texture2D::IID, textura, 0, false)?;
        let tamanho = buffer.cast::<IMF2DBuffer>()?.GetContiguousLength()?;
        buffer.SetCurrentLength(tamanho)?;
        let amostra = MFCreateSample()?;
        amostra.AddBuffer(&buffer)?;
        Ok(amostra)
    }
}

fn destrancar(transformacao: &IMFTransform) -> windows::core::Result<()> {
    unsafe { transformacao.GetAttributes()?.SetUINT32(&MF_TRANSFORM_ASYNC_UNLOCK, 1) }
}

fn gerente_da_placa(placa: &ID3D11Device) -> windows::core::Result<IMFDXGIDeviceManager> {
    let mut ficha = 0u32;
    let mut gerente = None;
    unsafe { MFCreateDXGIDeviceManager(&mut ficha, &mut gerente)? };
    let gerente: IMFDXGIDeviceManager = gerente.ok_or(E_POINTER)?;
    unsafe { gerente.ResetDevice(placa, ficha)? };
    Ok(gerente)
}
