use crate::ponte::{Escritor, Evento};
use livekit::prelude::Room;
use std::collections::HashSet;

const EU: &str = "";

#[derive(Default)]
pub struct QuemFala {
    falando: HashSet<String>,
}

impl QuemFala {
    pub fn atualizar(&mut self, sala: &Room, escritor: &Escritor) {
        let agora = quem_fala_agora(sala);
        for (par, ligado) in mudancas(&self.falando, &agora) {
            escritor.manda(&Evento::fala(&par, ligado));
        }
        self.falando = agora;
    }
}

fn quem_fala_agora(sala: &Room) -> HashSet<String> {
    let mut agora: HashSet<String> = sala
        .remote_participants()
        .into_iter()
        .filter(|(_, participante)| participante.is_speaking())
        .map(|(quem, _)| quem.into())
        .collect();
    if sala.local_participant().is_speaking() {
        agora.insert(EU.to_owned());
    }
    agora
}

fn mudancas(antes: &HashSet<String>, agora: &HashSet<String>) -> Vec<(String, bool)> {
    let comecaram = agora.difference(antes).map(|par| (par.clone(), true));
    let pararam = antes.difference(agora).map(|par| (par.clone(), false));
    comecaram.chain(pararam).collect()
}

#[cfg(test)]
mod testes {
    use super::*;

    fn conjunto(nomes: &[&str]) -> HashSet<String> {
        nomes.iter().map(|nome| nome.to_string()).collect()
    }

    #[test]
    fn so_emite_quem_comecou_ou_parou() {
        let mut vistas = mudancas(&conjunto(&["ana", "bia"]), &conjunto(&["bia", EU]));
        vistas.sort();
        assert_eq!(vistas, vec![(EU.to_string(), true), ("ana".to_string(), false)]);
    }

    #[test]
    fn sem_mudanca_nao_emite_nada() {
        assert!(mudancas(&conjunto(&["ana"]), &conjunto(&["ana"])).is_empty());
    }
}
