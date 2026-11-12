use windows::Win32::Media::MediaFoundation::{
    IMFAttributes, IMFByteStream, IMFMediaType, IMFSinkWriter, MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS,
    MF_SINK_WRITER_DISABLE_THROTTLING, MF_TRANSCODE_CONTAINERTYPE, MFCreateAttributes, MFCreateSinkWriterFromURL,
    MFTranscodeContainerType_MPEG4,
};
use windows::core::{Error, HSTRING, Result};

pub fn abrir_gravador(caminho: &str) -> Result<IMFSinkWriter> {
    let atributos = atributos_do_gravador()?;
    unsafe { MFCreateSinkWriterFromURL(&HSTRING::from(caminho), None::<&IMFByteStream>, &atributos) }
}

pub fn somar_fluxo(gravador: &IMFSinkWriter, saida: &IMFMediaType, entrada: &IMFMediaType) -> Result<u32> {
    unsafe {
        let fluxo = gravador.AddStream(saida)?;
        gravador.SetInputMediaType(fluxo, entrada, None::<&IMFAttributes>)?;
        Ok(fluxo)
    }
}

fn atributos_do_gravador() -> Result<IMFAttributes> {
    let mut atributos = None;
    unsafe {
        MFCreateAttributes(&mut atributos, 3)?;
        let atributos = atributos.ok_or_else(Error::empty)?;
        atributos.SetUINT32(&MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, 1)?;
        atributos.SetUINT32(&MF_SINK_WRITER_DISABLE_THROTTLING, 1)?;
        atributos.SetGUID(&MF_TRANSCODE_CONTAINERTYPE, &MFTranscodeContainerType_MPEG4)?;
        Ok(atributos)
    }
}
