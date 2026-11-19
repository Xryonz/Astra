use crate::tela::em;
use windows::Win32::Media::MediaFoundation::{
    IMFAttributes, IMFMediaType, IMFSourceReader, MF_MT_MAJOR_TYPE, MF_PD_DURATION,
    MF_SOURCE_READER_ENABLE_ADVANCED_VIDEO_PROCESSING, MF_SOURCE_READER_MEDIASOURCE, MFCreateAttributes,
    MFCreateSourceReaderFromURL, MFMediaType_Audio, MFMediaType_Video,
};
use windows::Win32::System::Variant::VT_UI8;
use windows::core::{Error, HSTRING};

const FLUXOS_OLHADOS: u32 = 32;

pub struct Fluxos {
    pub video: Option<u32>,
    pub som: Option<u32>,
}

pub fn abrir_leitor(caminho: &str, processar: bool) -> Result<IMFSourceReader, String> {
    let atributos = atributos_do_leitor(processar).map_err(em("criar atributos"))?;
    unsafe { MFCreateSourceReaderFromURL(&HSTRING::from(caminho), &atributos) }.map_err(em("abrir o vídeo"))
}

pub fn fluxos_do_arquivo(leitor: &IMFSourceReader) -> Fluxos {
    let mut fluxos = Fluxos { video: None, som: None };
    for indice in 0..FLUXOS_OLHADOS {
        let Ok(tipo) = (unsafe { leitor.GetNativeMediaType(indice, 0) }) else { break };
        match unsafe { tipo.GetGUID(&MF_MT_MAJOR_TYPE) } {
            Ok(maior) if maior == MFMediaType_Video && fluxos.video.is_none() => fluxos.video = Some(indice),
            Ok(maior) if maior == MFMediaType_Audio && fluxos.som.is_none() => fluxos.som = Some(indice),
            _ => {}
        }
    }
    fluxos
}

pub fn duracao_em_100ns(leitor: &IMFSourceReader) -> u64 {
    let fonte = MF_SOURCE_READER_MEDIASOURCE.0 as u32;
    let Ok(valor) = (unsafe { leitor.GetPresentationAttribute(fonte, &MF_PD_DURATION) }) else { return 0 };
    unsafe {
        let conteudo = &valor.Anonymous.Anonymous;
        if conteudo.vt == VT_UI8 { conteudo.Anonymous.uhVal } else { 0 }
    }
}

pub fn tipo_nativo(leitor: &IMFSourceReader, fluxo: u32) -> Result<IMFMediaType, String> {
    unsafe { leitor.GetNativeMediaType(fluxo, 0) }.map_err(em("ler o formato original"))
}

fn atributos_do_leitor(processar: bool) -> windows::core::Result<IMFAttributes> {
    let mut atributos = None;
    unsafe {
        MFCreateAttributes(&mut atributos, 1)?;
        let atributos = atributos.ok_or_else(Error::empty)?;
        if processar {
            atributos.SetUINT32(&MF_SOURCE_READER_ENABLE_ADVANCED_VIDEO_PROCESSING, 1)?;
        }
        Ok(atributos)
    }
}
