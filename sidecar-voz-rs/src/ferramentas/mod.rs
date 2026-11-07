mod bancada;
mod ensaio;
mod medidas;
mod relato;
mod relogio;

use crate::com::ComDaThread;
use crate::tela::placas_de_hardware;
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_FULL, MFShutdown, MFStartup};

pub fn rodar_se_pedida() -> Option<i32> {
    let pedida = std::env::args().nth(1)?;
    Some(match pedida.as_str() {
        "sondar-codificador" => sondar_codificador(),
        outra => {
            eprintln!("ferramenta desconhecida: {outra}");
            2
        }
    })
}

fn sondar_codificador() -> i32 {
    let _com = ComDaThread::iniciar();
    if let Err(e) = unsafe { MFStartup(MF_VERSION, MFSTARTUP_FULL) } {
        eprintln!("iniciar o Media Foundation: {e}");
        return 1;
    }
    let codigo = match placas_de_hardware() {
        Ok(placas) => {
            for placa in &placas {
                println!("{}", relato::relatar(placa));
            }
            0
        }
        Err(e) => {
            eprintln!("listar as placas: {e}");
            1
        }
    };
    let _ = unsafe { MFShutdown() };
    codigo
}
