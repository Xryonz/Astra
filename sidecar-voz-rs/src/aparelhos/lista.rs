use crate::com::ComDaThread;
use serde::Serialize;
use windows::Win32::Devices::FunctionDiscovery::PKEY_Device_FriendlyName;
use windows::Win32::Media::Audio::{
    DEVICE_STATE_ACTIVE, EDataFlow, IMMDevice, IMMDeviceEnumerator, MMDeviceEnumerator, eCapture, eCommunications,
    eConsole, eRender,
};
use windows::Win32::System::Com::StructuredStorage::{PropVariantClear, PropVariantToStringAlloc};
use windows::Win32::System::Com::{CLSCTX_ALL, CoCreateInstance, CoTaskMemFree, STGM_READ};
use windows::core::{PWSTR, Result};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Sentido {
    Entrada,
    Saida,
}

impl Sentido {
    pub fn do_comando(sentido: &str) -> Self {
        if sentido == "entrada" { Self::Entrada } else { Self::Saida }
    }

    pub fn nome(self) -> &'static str {
        match self {
            Self::Entrada => "entrada",
            Self::Saida => "saida",
        }
    }

    fn fluxo(self) -> EDataFlow {
        match self {
            Self::Entrada => eCapture,
            Self::Saida => eRender,
        }
    }
}

#[derive(Clone, Debug, Serialize)]
pub struct Aparelho {
    pub id: String,
    pub nome: String,
}

pub fn listar(sentido: Sentido) -> Result<Vec<Aparelho>> {
    let _com = ComDaThread::iniciar();
    let colecao = unsafe { enumerador()?.EnumAudioEndpoints(sentido.fluxo(), DEVICE_STATE_ACTIVE)? };
    let total = unsafe { colecao.GetCount()? };
    Ok((0..total).filter_map(|i| unsafe { colecao.Item(i) }.ok().and_then(|a| descrever(&a).ok())).collect())
}

pub fn padrao(sentido: Sentido) -> Option<String> {
    let _com = ComDaThread::iniciar();
    let enumerador = enumerador().ok()?;
    [eCommunications, eConsole].into_iter().find_map(|papel| {
        let aparelho = unsafe { enumerador.GetDefaultAudioEndpoint(sentido.fluxo(), papel) }.ok()?;
        id_de(&aparelho).ok()
    })
}

pub(super) fn enumerador() -> Result<IMMDeviceEnumerator> {
    unsafe { CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL) }
}

fn descrever(aparelho: &IMMDevice) -> Result<Aparelho> {
    Ok(Aparelho { id: id_de(aparelho)?, nome: nome_de(aparelho)? })
}

fn id_de(aparelho: &IMMDevice) -> Result<String> {
    let id = unsafe { aparelho.GetId()? };
    Ok(texto_liberado(id))
}

fn nome_de(aparelho: &IMMDevice) -> Result<String> {
    unsafe {
        let propriedades = aparelho.OpenPropertyStore(STGM_READ)?;
        let mut valor = propriedades.GetValue(&PKEY_Device_FriendlyName)?;
        let nome = PropVariantToStringAlloc(&valor).map(texto_liberado);
        let _ = PropVariantClear(&mut valor);
        nome
    }
}

fn texto_liberado(texto: PWSTR) -> String {
    let lido = unsafe { texto.to_string() }.unwrap_or_default();
    unsafe { CoTaskMemFree(Some(texto.0 as *const _)) };
    lido
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn sentido_do_comando_segue_o_go() {
        assert_eq!(Sentido::do_comando("entrada"), Sentido::Entrada);
        assert_eq!(Sentido::do_comando("saida"), Sentido::Saida);
        assert_eq!(Sentido::do_comando(""), Sentido::Saida);
    }
}
