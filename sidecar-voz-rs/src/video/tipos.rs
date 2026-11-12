use windows::Win32::Media::MediaFoundation::{
    IMFMediaType, MF_MT_AUDIO_AVG_BYTES_PER_SECOND, MF_MT_AUDIO_BITS_PER_SAMPLE, MF_MT_AUDIO_BLOCK_ALIGNMENT,
    MF_MT_AUDIO_NUM_CHANNELS, MF_MT_AUDIO_SAMPLES_PER_SECOND, MF_MT_AVG_BITRATE, MF_MT_FRAME_RATE, MF_MT_FRAME_SIZE,
    MF_MT_INTERLACE_MODE, MF_MT_MAJOR_TYPE, MF_MT_MPEG2_PROFILE, MF_MT_PIXEL_ASPECT_RATIO, MF_MT_SUBTYPE,
    MFAudioFormat_AAC, MFAudioFormat_PCM, MFCreateMediaType, MFMediaType_Audio, MFMediaType_Video, MFVideoFormat_H264,
    MFVideoFormat_NV12, MFVideoInterlace_Progressive, eAVEncH264VProfile_Main,
};
use windows::core::{GUID, Result};

#[derive(Clone, Copy)]
pub struct Imagem {
    pub largura: u32,
    pub altura: u32,
    pub taxa: u64,
}

#[derive(Clone, Copy)]
pub struct Som {
    pub amostras: u32,
    pub canais: u32,
}

pub fn par(alto: u32, baixo: u32) -> u64 {
    (u64::from(alto) << 32) | u64::from(baixo)
}

pub fn tipo_nv12(imagem: Imagem) -> Result<IMFMediaType> {
    let tipo = tipo_de_imagem(MFVideoFormat_NV12, imagem)?;
    unsafe { tipo.SetUINT64(&MF_MT_PIXEL_ASPECT_RATIO, par(1, 1))? };
    Ok(tipo)
}

pub fn tipo_h264(imagem: Imagem, kbps: u32) -> Result<IMFMediaType> {
    let tipo = tipo_de_imagem(MFVideoFormat_H264, imagem)?;
    unsafe {
        tipo.SetUINT32(&MF_MT_AVG_BITRATE, kbps * 1000)?;
        tipo.SetUINT64(&MF_MT_PIXEL_ASPECT_RATIO, par(1, 1))?;
        tipo.SetUINT32(&MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_Main.0 as u32)?;
    }
    Ok(tipo)
}

pub fn tipo_pcm(som: Som) -> Result<IMFMediaType> {
    let tipo = tipo_de_som(MFAudioFormat_PCM, som)?;
    unsafe {
        tipo.SetUINT32(&MF_MT_AUDIO_BLOCK_ALIGNMENT, som.canais * 2)?;
        tipo.SetUINT32(&MF_MT_AUDIO_AVG_BYTES_PER_SECOND, som.amostras * som.canais * 2)?;
    }
    Ok(tipo)
}

pub fn tipo_aac(som: Som, kbps: u32) -> Result<IMFMediaType> {
    let tipo = tipo_de_som(MFAudioFormat_AAC, som)?;
    unsafe { tipo.SetUINT32(&MF_MT_AUDIO_AVG_BYTES_PER_SECOND, kbps * 1000 / 8)? };
    Ok(tipo)
}

fn tipo_de_imagem(subtipo: GUID, imagem: Imagem) -> Result<IMFMediaType> {
    unsafe {
        let tipo = MFCreateMediaType()?;
        tipo.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video)?;
        tipo.SetGUID(&MF_MT_SUBTYPE, &subtipo)?;
        tipo.SetUINT64(&MF_MT_FRAME_SIZE, par(imagem.largura, imagem.altura))?;
        tipo.SetUINT64(&MF_MT_FRAME_RATE, imagem.taxa)?;
        tipo.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32)?;
        Ok(tipo)
    }
}

fn tipo_de_som(subtipo: GUID, som: Som) -> Result<IMFMediaType> {
    unsafe {
        let tipo = MFCreateMediaType()?;
        tipo.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Audio)?;
        tipo.SetGUID(&MF_MT_SUBTYPE, &subtipo)?;
        tipo.SetUINT32(&MF_MT_AUDIO_BITS_PER_SAMPLE, 16)?;
        tipo.SetUINT32(&MF_MT_AUDIO_SAMPLES_PER_SECOND, som.amostras)?;
        tipo.SetUINT32(&MF_MT_AUDIO_NUM_CHANNELS, som.canais)?;
        Ok(tipo)
    }
}
