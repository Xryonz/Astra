use crate::tela::{
    Caminho, Codificacao, Configuracao, Entrada, Origem, Placa, abrir_codificacao, codificadores_h264_da_placa,
    codificadores_h264_de_software, em, placa_padrao, placas_de_hardware, teto_sem_placa,
};
use std::collections::HashSet;

const QUADROS_QUE_PROVAM_O_CODIFICADOR: u32 = 120;

pub struct Aberta {
    pub codificacao: Codificacao,
    pub identidade: String,
}

pub fn abrir_o_primeiro_que_aceitar(
    config: Configuracao,
    exigida: Option<&Placa>,
    condenados: &HashSet<String>,
) -> Result<Aberta, String> {
    let mut motivos = Vec::new();
    if let Some(aberta) = abrir_na_placa(config, exigida, condenados, &mut motivos)? {
        return Ok(aberta);
    }
    if let Some(aberta) = abrir_na_memoria(config, exigida, condenados, &mut motivos)? {
        return Ok(aberta);
    }
    Err(if motivos.is_empty() { "nenhum codificador H.264 nesta máquina".into() } else { motivos.join("; ") })
}

fn abrir_na_placa(
    config: Configuracao,
    exigida: Option<&Placa>,
    condenados: &HashSet<String>,
    motivos: &mut Vec<String>,
) -> Result<Option<Aberta>, String> {
    let placas = placas_de_hardware().map_err(em("listar as placas"))?;
    for placa in placas.iter().filter(|placa| exigida.is_none_or(|exigida| exigida.luid == placa.luid)) {
        let codificadores = match codificadores_h264_da_placa(placa.luid) {
            Ok(codificadores) => codificadores,
            Err(erro) => {
                motivos.push(format!("{}: listar os codificadores: {erro}", placa.nome));
                continue;
            }
        };
        for (posicao, codificador) in codificadores.into_iter().enumerate() {
            let identidade = identidade(placa, posicao);
            let nome = codificador.nome.clone();
            if condenados.contains(&identidade) {
                motivos.push(format!("{nome}: já falhou nesta transmissão"));
                continue;
            }
            match abrir_codificacao(placa, codificador, config, Origem::Capturada, Caminho::Placa) {
                Ok(codificacao) => return Ok(Some(Aberta { codificacao, identidade })),
                Err(motivo) => motivos.push(format!("{nome} ({}): {motivo}", placa.nome)),
            }
        }
    }
    Ok(None)
}

fn abrir_na_memoria(
    config: Configuracao,
    exigida: Option<&Placa>,
    condenados: &HashSet<String>,
    motivos: &mut Vec<String>,
) -> Result<Option<Aberta>, String> {
    let padrao;
    let placa = match exigida {
        Some(placa) => placa,
        None => {
            padrao = placa_padrao().map_err(em("achar a placa padrão"))?;
            &padrao
        }
    };
    let (largura, altura) = teto_sem_placa(config.largura, config.altura);
    let config = Configuracao { largura, altura, entrada: Entrada::Nv12, ..config };
    let codificadores = codificadores_h264_de_software().map_err(em("listar os codificadores de software"))?;
    for (posicao, codificador) in codificadores.into_iter().enumerate() {
        let identidade = format!("software#{posicao}");
        let nome = codificador.nome.clone();
        if condenados.contains(&identidade) {
            motivos.push(format!("{nome}: já falhou nesta transmissão"));
            continue;
        }
        match abrir_codificacao(placa, codificador, config, Origem::Capturada, Caminho::Memoria) {
            Ok(codificacao) => return Ok(Some(Aberta { codificacao, identidade })),
            Err(motivo) => motivos.push(format!("{nome} (na memória): {motivo}")),
        }
    }
    Ok(None)
}

pub fn condenar(condenados: &mut HashSet<String>, identidade: &str, quadros: u32) -> bool {
    if quadros >= QUADROS_QUE_PROVAM_O_CODIFICADOR {
        return false;
    }
    condenados.insert(identidade.to_owned());
    true
}

fn identidade(placa: &Placa, posicao: usize) -> String {
    format!("{:08X}:{:08X}#{posicao}", placa.luid.HighPart, placa.luid.LowPart)
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn quem_prova_os_120_quadros_e_reaberto_e_nao_condenado() {
        let mut condenados = HashSet::new();
        assert!(!condenar(&mut condenados, "intel#0", QUADROS_QUE_PROVAM_O_CODIFICADOR));
        assert!(!condenar(&mut condenados, "intel#0", QUADROS_QUE_PROVAM_O_CODIFICADOR + 1));
        assert!(condenados.is_empty());
    }

    #[test]
    fn quem_morre_cedo_sai_da_lista() {
        let mut condenados = HashSet::new();
        assert!(condenar(&mut condenados, "nvidia#0", 0));
        assert!(condenar(&mut condenados, "intel#1", QUADROS_QUE_PROVAM_O_CODIFICADOR - 1));
        assert!(condenados.contains("nvidia#0") && condenados.contains("intel#1"));
    }
}
