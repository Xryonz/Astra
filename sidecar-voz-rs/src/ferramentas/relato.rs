use super::ensaio::{ResultadoDoEnsaio, ensaiar_com_limite};
use super::relogio::Relogio;
use crate::tela::{Entrada, Placa, codificadores_h264_da_placa, nome_do_fabricante};
use serde::Serialize;

const ENSAIOS: [(Entrada, Relogio); 4] = [
    (Entrada::Argb, Relogio::Padrao),
    (Entrada::Nv12, Relogio::Padrao),
    (Entrada::Argb, Relogio::Fino),
    (Entrada::Nv12, Relogio::Fino),
];

#[derive(Serialize)]
struct RelatoDaPlaca {
    placa: String,
    fabricante: &'static str,
    codigo: String,
    luid: String,
    codificadores: Vec<String>,
    ensaios: Vec<ResultadoDoEnsaio>,
}

pub fn relatar(placa: &Placa) -> String {
    let (codificadores, ensaios) = match codificadores_h264_da_placa(placa.luid) {
        Ok(lista) if !lista.is_empty() => (
            lista.iter().map(|codificador| codificador.nome.clone()).collect(),
            ENSAIOS.iter().map(|&(entrada, relogio)| ensaiar_com_limite(placa, entrada, relogio)).collect(),
        ),
        Ok(_) => (Vec::new(), Vec::new()),
        Err(e) => (vec![format!("erro: {e}")], Vec::new()),
    };
    let relato = RelatoDaPlaca {
        placa: placa.nome.clone(),
        fabricante: nome_do_fabricante(placa.fabricante),
        codigo: format!("0x{:04X}", placa.fabricante),
        luid: format!("{:08X}:{:08X}", placa.luid.HighPart, placa.luid.LowPart),
        codificadores,
        ensaios,
    };
    serde_json::to_string(&relato).unwrap_or_default()
}
