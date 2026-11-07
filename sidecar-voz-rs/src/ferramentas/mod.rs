mod ensaio;
mod medidas;
mod relato;
mod relogio;
mod sonda_da_janela;

use crate::com::ComDaThread;
use crate::tela::placas_de_hardware;
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_FULL, MFShutdown, MFStartup};

pub fn rodar_se_pedida() -> Option<i32> {
    let mut argumentos = std::env::args().skip(1);
    let pedida = argumentos.next()?;
    Some(match pedida.as_str() {
        "sondar-codificador" => sondar_codificador(),
        "sondar-janela" => {
            let mut numero = || argumentos.next().and_then(|texto| texto.parse().ok());
            match (numero(), numero()) {
                (Some(janela), Some(segundos)) => sonda_da_janela::sondar_janela(janela, segundos),
                _ => {
                    eprintln!("uso: sondar-janela <janela> <segundos>");
                    2
                }
            }
        }
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
