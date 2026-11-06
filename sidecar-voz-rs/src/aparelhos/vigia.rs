use super::com::ComDaThread;
use super::lista::enumerador;
use tokio::sync::mpsc::UnboundedSender;
use windows::Win32::Foundation::PROPERTYKEY;
use windows::Win32::Media::Audio::{
    DEVICE_STATE, EDataFlow, ERole, IMMDeviceEnumerator, IMMNotificationClient, IMMNotificationClient_Impl,
    eCommunications,
};
use windows::core::{PCWSTR, Result, implement};

pub struct Vigia {
    enumerador: IMMDeviceEnumerator,
    aviso: IMMNotificationClient,
    _com: ComDaThread,
}

pub fn vigiar(mudou: UnboundedSender<()>) -> Result<Vigia> {
    let com = ComDaThread::iniciar();
    let enumerador = enumerador()?;
    let aviso: IMMNotificationClient = Aviso { mudou }.into();
    unsafe { enumerador.RegisterEndpointNotificationCallback(&aviso)? };
    Ok(Vigia { enumerador, aviso, _com: com })
}

impl Drop for Vigia {
    fn drop(&mut self) {
        let _ = unsafe { self.enumerador.UnregisterEndpointNotificationCallback(&self.aviso) };
    }
}

#[implement(IMMNotificationClient)]
struct Aviso {
    mudou: UnboundedSender<()>,
}

impl Aviso {
    fn avisar(&self) {
        let _ = self.mudou.send(());
    }
}

impl IMMNotificationClient_Impl for Aviso_Impl {
    fn OnDeviceStateChanged(&self, _: &PCWSTR, _: DEVICE_STATE) -> Result<()> {
        self.avisar();
        Ok(())
    }

    fn OnDeviceAdded(&self, _: &PCWSTR) -> Result<()> {
        Ok(())
    }

    fn OnDeviceRemoved(&self, _: &PCWSTR) -> Result<()> {
        self.avisar();
        Ok(())
    }

    fn OnDefaultDeviceChanged(&self, _: EDataFlow, papel: ERole, _: &PCWSTR) -> Result<()> {
        if papel == eCommunications {
            self.avisar();
        }
        Ok(())
    }

    fn OnPropertyValueChanged(&self, _: &PCWSTR, _: &PROPERTYKEY) -> Result<()> {
        Ok(())
    }
}
