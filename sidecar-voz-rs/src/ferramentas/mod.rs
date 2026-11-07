mod ensaio;
mod medidas;
mod relato;
mod relogio;
mod sonda_da_janela;

use crate::com::ComDaThread;
use crate::tela::{miniaturas_das_janelas, placas_de_hardware};
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
        "sondar-miniatura" => match (argumentos.next().and_then(|texto| texto.parse().ok()), argumentos.next()) {
            (Some(janela), Some(arquivo)) => sondar_miniatura(janela, &arquivo),
            _ => {
                eprintln!("uso: sondar-miniatura <janela> <arquivo.png>");
                2
            }
        },
        outra => {
            eprintln!("ferramenta desconhecida: {outra}");
            2
        }
    })
}

fn sondar_miniatura(janela: u64, arquivo: &str) -> i32 {
    let _com = ComDaThread::iniciar();
    let mut feita = None;
    miniaturas_das_janelas(&[janela], |_, png| feita = Some(png));
    let Some(png) = feita else {
        eprintln!("a janela {janela} não deu miniatura");
        return 1;
    };
    if let Err(erro) = std::fs::write(arquivo, &png) {
        eprintln!("gravar {arquivo}: {erro}");
        return 1;
    }
    println!("{{\"bytes\":{}}}", png.len());
    0
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
            println!("{}", relato::relatar_sem_placa());
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
