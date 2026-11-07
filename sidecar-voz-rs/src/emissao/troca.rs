use crate::tela::{
    Codificacao, Configuracao, Origem, Placa, abrir_codificacao, codificadores_h264_da_placa, em, placas_de_hardware,
};
use std::collections::HashSet;

const QUADROS_QUE_PROVAM_O_CODIFICADOR: u32 = 120;

pub struct Aberta {
    pub codificacao: Codificacao,
    pub identidade: String,
}

pub fn abrir_o_primeiro_que_aceitar(
    config: Configuracao,
    origem: Origem,
    condenados: &HashSet<String>,
) -> Result<Aberta, String> {
    let mut motivos = Vec::new();
    for placa in placas_de_hardware().map_err(em("listar as placas"))? {
        let codificadores = match codificadores_h264_da_placa(placa.luid) {
            Ok(codificadores) => codificadores,
            Err(erro) => {
                motivos.push(format!("{}: listar os codificadores: {erro}", placa.nome));
                continue;
            }
        };
        for (posicao, codificador) in codificadores.into_iter().enumerate() {
            let identidade = identidade(&placa, posicao);
            let nome = codificador.nome.clone();
            if condenados.contains(&identidade) {
                motivos.push(format!("{nome}: já falhou nesta transmissão"));
                continue;
            }
            match abrir_codificacao(&placa, codificador, config, origem) {
                Ok(codificacao) => return Ok(Aberta { codificacao, identidade }),
                Err(motivo) => motivos.push(format!("{nome} ({}): {motivo}", placa.nome)),
            }
        }
    }
    Err(if motivos.is_empty() { "nenhum codificador H.264 de hardware".into() } else { motivos.join("; ") })
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
