use crate::com::ComDaThread;
use crate::video::{Ajustes, Ficha, comprimir, sondar};
use serde::Serialize;
use std::io::Write;
use windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_NOSOCKET, MFShutdown, MFStartup};

const USO: &str = "uso: sondar-video <arquivo> | comprimir-video <entrada> <saída> <lado-menor> <kbps> <kbps-do-som>";

#[derive(Serialize)]
#[serde(tag = "ev", rename_all = "camelCase")]
enum Evento<'a> {
    Ficha(&'a Ficha),
    Andamento { fracao: f64 },
    Pronto,
    Erro { msg: &'a str },
}

struct MediaFoundation;

impl MediaFoundation {
    fn iniciar() -> Result<Self, String> {
        unsafe { MFStartup(MF_VERSION, MFSTARTUP_NOSOCKET) }
            .map(|()| Self)
            .map_err(|erro| format!("iniciar o Media Foundation: {erro}"))
    }
}

impl Drop for MediaFoundation {
    fn drop(&mut self) {
        let _ = unsafe { MFShutdown() };
    }
}

pub fn rodar(pedida: &str, argumentos: &[String]) -> i32 {
    vigiar_a_entrada();
    let _com = ComDaThread::iniciar();
    match MediaFoundation::iniciar().and_then(|_media| atender(pedida, argumentos)) {
        Ok(()) => 0,
        Err(msg) => {
            emitir(&Evento::Erro { msg: &msg });
            1
        }
    }
}

fn atender(pedida: &str, argumentos: &[String]) -> Result<(), String> {
    match (pedida, argumentos) {
        ("sondar-video", [arquivo]) => sondar(arquivo).map(|ficha| emitir(&Evento::Ficha(&ficha))),
        ("comprimir-video", [entrada, saida, lado, kbps, kbps_do_som]) => {
            let ajustes = Ajustes { lado_menor: numero(lado)?, kbps: numero(kbps)?, kbps_do_som: numero(kbps_do_som)? };
            comprimir(entrada, saida, &ajustes, |fracao| emitir(&Evento::Andamento { fracao }))
                .inspect_err(|_| {
                    let _ = std::fs::remove_file(saida);
                })
                .map(|()| emitir(&Evento::Pronto))
        }
        _ => Err(USO.to_string()),
    }
}

fn numero(texto: &str) -> Result<u32, String> {
    texto.parse().ok().filter(|&valor| valor > 0).ok_or_else(|| format!("ajuste inválido: {texto:?}"))
}

fn emitir(evento: &Evento) {
    if let Ok(linha) = serde_json::to_string(evento) {
        let _ = writeln!(std::io::stdout().lock(), "{linha}");
    }
}

fn vigiar_a_entrada() {
    std::thread::spawn(|| {
        let _ = std::io::copy(&mut std::io::stdin().lock(), &mut std::io::sink());
        std::process::exit(1);
    });
}
