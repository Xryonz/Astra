use crate::com::ComDaThread;
use crate::tela::{Placa, codificadores_h264_da_placa, nome_do_fabricante, placas_de_hardware};
use serde::Serialize;
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_FULL, MFShutdown, MFStartup};

#[derive(Serialize)]
struct RelatoDaPlaca {
    placa: String,
    fabricante: &'static str,
    codigo: String,
    luid: String,
    codificadores: Vec<String>,
}

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
            for placa in placas {
                println!("{}", relatar(&placa));
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

fn relatar(placa: &Placa) -> String {
    let codificadores = codificadores_h264_da_placa(placa.luid).unwrap_or_else(|e| vec![format!("erro: {e}")]);
    let relato = RelatoDaPlaca {
        placa: placa.nome.clone(),
        fabricante: nome_do_fabricante(placa.fabricante),
        codigo: format!("0x{:04X}", placa.fabricante),
        luid: format!("{:08X}:{:08X}", placa.luid.HighPart, placa.luid.LowPart),
        codificadores,
    };
    serde_json::to_string(&relato).unwrap_or_default()
}
