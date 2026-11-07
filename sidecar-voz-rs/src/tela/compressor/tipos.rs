use super::Configuracao;
use crate::tela::etapa::em;
use windows::Win32::Media::MediaFoundation::{
    IMFMediaType, IMFTransform, MF_MT_AVG_BITRATE, MF_MT_FRAME_RATE, MF_MT_FRAME_SIZE, MF_MT_INTERLACE_MODE,
    MF_MT_MAJOR_TYPE, MF_MT_MAX_KEYFRAME_SPACING, MF_MT_MPEG2_PROFILE, MF_MT_SUBTYPE, MFCreateMediaType,
    MFMediaType_Video, MFVideoFormat_H264, MFVideoInterlace_Progressive, eAVEncH264VProfile_Base,
    eAVEncH264VProfile_Main,
};
use windows::core::GUID;

pub fn configurar_saida(transformacao: &IMFTransform, config: &Configuracao) -> Result<u32, String> {
    let main = eAVEncH264VProfile_Main.0 as u32;
    let baseline = eAVEncH264VProfile_Base.0 as u32;
    saida_com_perfil(transformacao, config, main)
        .map(|()| main)
        .or_else(|_| saida_com_perfil(transformacao, config, baseline).map(|()| baseline))
        .map_err(em("definir a saída H.264"))
}

pub fn saida_com_perfil(transformacao: &IMFTransform, config: &Configuracao, perfil: u32) -> windows::core::Result<()> {
    unsafe {
        let tipo = MFCreateMediaType()?;
        tipo.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video)?;
        tipo.SetGUID(&MF_MT_SUBTYPE, &MFVideoFormat_H264)?;
        tipo.SetUINT32(&MF_MT_AVG_BITRATE, config.kbps * 1000)?;
        tipo.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32)?;
        tipo.SetUINT32(&MF_MT_MPEG2_PROFILE, perfil)?;
        tipo.SetUINT32(&MF_MT_MAX_KEYFRAME_SPACING, config.fps * 2)?;
        tipo.SetUINT64(&MF_MT_FRAME_SIZE, par(config.largura, config.altura))?;
        tipo.SetUINT64(&MF_MT_FRAME_RATE, par(config.fps, 1))?;
        transformacao.SetOutputType(0, &tipo, 0)
    }
}

pub fn configurar_entrada(transformacao: &IMFTransform, config: &Configuracao) -> Result<(), String> {
    let tipo = tipo_de_entrada_aceito(transformacao, config.entrada.subtipo())
        .ok_or_else(|| format!("o codificador não aceita {} na entrada", config.entrada.nome()))?;
    amarrar_entrada(transformacao, &tipo, config).map_err(em("amarrar a entrada"))
}

fn tipo_de_entrada_aceito(transformacao: &IMFTransform, subtipo: GUID) -> Option<IMFMediaType> {
    (0u32..)
        .map_while(|indice| unsafe { transformacao.GetInputAvailableType(0, indice) }.ok())
        .find(|tipo| unsafe { tipo.GetGUID(&MF_MT_SUBTYPE) }.ok() == Some(subtipo))
}

fn amarrar_entrada(transformacao: &IMFTransform, tipo: &IMFMediaType, config: &Configuracao) -> windows::core::Result<()> {
    unsafe {
        tipo.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32)?;
        tipo.SetUINT64(&MF_MT_FRAME_SIZE, par(config.largura, config.altura))?;
        tipo.SetUINT64(&MF_MT_FRAME_RATE, par(config.fps, 1))?;
        transformacao.SetInputType(0, tipo, 0)
    }
}

fn par(alto: u32, baixo: u32) -> u64 {
    (u64::from(alto) << 32) | u64::from(baixo)
}
