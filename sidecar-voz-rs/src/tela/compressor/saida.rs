use std::mem::ManuallyDrop;
use windows::Win32::Media::MediaFoundation::{
    IMFSample, IMFTransform, MF_E_TRANSFORM_NEED_MORE_INPUT, MF_E_TRANSFORM_STREAM_CHANGE, MFCreateMemoryBuffer,
    MFCreateSample, MFT_OUTPUT_DATA_BUFFER,
};

pub enum Puxado {
    Pacote { tempo: Option<i64>, bytes: Vec<u8> },
    Nada,
    MudouOFormato,
}

pub fn puxar(transformacao: &IMFTransform, traz_amostra: bool, tamanho: u32) -> windows::core::Result<Puxado> {
    let nossa = if traz_amostra { None } else { Some(amostra_vazia(tamanho)?) };
    let mut saidas = [MFT_OUTPUT_DATA_BUFFER {
        dwStreamID: 0,
        pSample: ManuallyDrop::new(nossa),
        dwStatus: 0,
        pEvents: ManuallyDrop::new(None),
    }];
    let mut estado = 0u32;
    let feito = unsafe { transformacao.ProcessOutput(0, &mut saidas, &mut estado) };
    let [saida] = saidas;
    drop(ManuallyDrop::into_inner(saida.pEvents));
    let amostra = ManuallyDrop::into_inner(saida.pSample);
    match (feito, amostra) {
        (Err(erro), _) if erro.code() == MF_E_TRANSFORM_NEED_MORE_INPUT => Ok(Puxado::Nada),
        (Err(erro), _) if erro.code() == MF_E_TRANSFORM_STREAM_CHANGE => Ok(Puxado::MudouOFormato),
        (Err(erro), _) => Err(erro),
        (Ok(()), None) => Ok(Puxado::Nada),
        (Ok(()), Some(amostra)) => Ok(Puxado::Pacote {
            tempo: unsafe { amostra.GetSampleTime() }.ok(),
            bytes: bytes_da_amostra(&amostra)?,
        }),
    }
}

fn amostra_vazia(tamanho: u32) -> windows::core::Result<IMFSample> {
    unsafe {
        let buffer = MFCreateMemoryBuffer(tamanho)?;
        let amostra = MFCreateSample()?;
        amostra.AddBuffer(&buffer)?;
        Ok(amostra)
    }
}

fn bytes_da_amostra(amostra: &IMFSample) -> windows::core::Result<Vec<u8>> {
    unsafe {
        let buffer = amostra.ConvertToContiguousBuffer()?;
        let mut inicio = std::ptr::null_mut();
        let mut tamanho = 0u32;
        buffer.Lock(&mut inicio, None, Some(&mut tamanho))?;
        let bytes = if tamanho == 0 { Vec::new() } else { std::slice::from_raw_parts(inicio, tamanho as usize).to_vec() };
        buffer.Unlock()?;
        Ok(bytes)
    }
}
