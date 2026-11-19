use std::sync::mpsc::{Sender, channel};
use windows::Win32::System::Power::{
    ES_CONTINUOUS, ES_DISPLAY_REQUIRED, ES_SYSTEM_REQUIRED, EXECUTION_STATE, SetThreadExecutionState,
};

pub struct Vigilia {
    pedidos: Sender<bool>,
}

impl Vigilia {
    pub fn nova() -> Self {
        let (pedidos, chegam) = channel::<bool>();
        let aberta = std::thread::Builder::new().name("astra-vigilia".into()).spawn(move || {
            for acordado in chegam {
                segurar(acordado);
            }
        });
        if let Err(erro) = aberta {
            eprintln!("abrir a linha da vigília: {erro}");
        }
        Self { pedidos }
    }

    pub fn manter(&self, acordado: bool) {
        let _ = self.pedidos.send(acordado);
    }
}

fn segurar(acordado: bool) {
    let estado = if acordado { ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED } else { ES_CONTINUOUS };
    if unsafe { SetThreadExecutionState(estado) } == EXECUTION_STATE(0) {
        eprintln!("manter a tela acordada: o Windows recusou");
    }
}
