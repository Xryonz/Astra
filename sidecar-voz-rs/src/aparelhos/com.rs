use windows::Win32::System::Com::{COINIT_MULTITHREADED, CoInitializeEx, CoUninitialize};

pub struct ComDaThread {
    iniciado: bool,
}

impl ComDaThread {
    pub fn iniciar() -> Self {
        let resultado = unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) };
        Self { iniciado: resultado.is_ok() }
    }
}

impl Drop for ComDaThread {
    fn drop(&mut self) {
        if self.iniciado {
            unsafe { CoUninitialize() };
        }
    }
}
