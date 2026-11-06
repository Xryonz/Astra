use windows::Win32::Foundation::{E_POINTER, LUID};
use windows::Win32::Media::MediaFoundation::{
    IMFActivate, IMFAttributes, MFCreateAttributes, MFMediaType_Video, MFT_CATEGORY_VIDEO_ENCODER,
    MFT_ENUM_ADAPTER_LUID, MFT_ENUM_FLAG, MFT_ENUM_FLAG_HARDWARE, MFT_ENUM_FLAG_SORTANDFILTER,
    MFT_FRIENDLY_NAME_Attribute, MFT_REGISTER_TYPE_INFO, MFTEnum2, MFVideoFormat_H264,
};
use windows::Win32::System::Com::CoTaskMemFree;
use windows::core::PWSTR;

pub fn codificadores_h264_da_placa(luid: LUID) -> windows::core::Result<Vec<String>> {
    let atributos = atributos_da_placa(luid)?;
    let saida = MFT_REGISTER_TYPE_INFO { guidMajorType: MFMediaType_Video, guidSubtype: MFVideoFormat_H264 };
    let bandeiras = MFT_ENUM_FLAG(MFT_ENUM_FLAG_HARDWARE.0 | MFT_ENUM_FLAG_SORTANDFILTER.0);
    let mut lista: *mut Option<IMFActivate> = std::ptr::null_mut();
    let mut quantos = 0u32;
    unsafe {
        MFTEnum2(MFT_CATEGORY_VIDEO_ENCODER, bandeiras, None, Some(&saida), &atributos, &mut lista, &mut quantos)?;
    }
    Ok(nomes_liberando(lista, quantos))
}

fn atributos_da_placa(luid: LUID) -> windows::core::Result<IMFAttributes> {
    let mut atributos = None;
    unsafe { MFCreateAttributes(&mut atributos, 1)? };
    let atributos: IMFAttributes = atributos.ok_or(E_POINTER)?;
    let bytes = [luid.LowPart.to_le_bytes(), luid.HighPart.to_le_bytes()].concat();
    unsafe { atributos.SetBlob(&MFT_ENUM_ADAPTER_LUID, &bytes)? };
    Ok(atributos)
}

fn nomes_liberando(lista: *mut Option<IMFActivate>, quantos: u32) -> Vec<String> {
    if lista.is_null() {
        return Vec::new();
    }
    let ativadores = unsafe { std::slice::from_raw_parts_mut(lista, quantos as usize) };
    let nomes = ativadores.iter().flatten().map(nome_amigavel).collect();
    ativadores.iter_mut().for_each(|ativador| drop(ativador.take()));
    unsafe { CoTaskMemFree(Some(lista as *const _)) };
    nomes
}

fn nome_amigavel(ativador: &IMFActivate) -> String {
    let mut texto = PWSTR::null();
    let mut tamanho = 0u32;
    if unsafe { ativador.GetAllocatedString(&MFT_FRIENDLY_NAME_Attribute, &mut texto, &mut tamanho) }.is_err() {
        return "sem nome".into();
    }
    let nome = unsafe { texto.to_string() }.unwrap_or_default();
    unsafe { CoTaskMemFree(Some(texto.0 as *const _)) };
    nome
}
