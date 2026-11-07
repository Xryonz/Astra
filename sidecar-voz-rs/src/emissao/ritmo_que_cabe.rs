use std::cmp::Ordering;
use std::time::Duration;

const TAXAS_OFERECIDAS: [u32; 2] = [60, 30];
const SEGUNDOS_PARA_DESCER: u32 = 2;
const SEGUNDOS_PARA_SUBIR: u32 = 10;

pub struct RitmoQueCabe {
    teto: u32,
    apertados: u32,
    folgados: u32,
}

impl RitmoQueCabe {
    pub fn novo(teto: u32) -> Self {
        Self { teto, apertados: 0, folgados: 0 }
    }

    pub fn segundo(&mut self, trabalho: Duration, atual: u32) -> Option<u32> {
        let cabe = taxa_que_cabe(trabalho, self.teto);
        match cabe.cmp(&atual) {
            Ordering::Less => (self.apertados, self.folgados) = (self.apertados + 1, 0),
            Ordering::Greater => (self.apertados, self.folgados) = (0, self.folgados + 1),
            Ordering::Equal => {
                (self.apertados, self.folgados) = (0, 0);
                return None;
            }
        }
        if self.apertados < SEGUNDOS_PARA_DESCER && self.folgados < SEGUNDOS_PARA_SUBIR {
            return None;
        }
        (self.apertados, self.folgados) = (0, 0);
        Some(cabe)
    }
}

fn taxa_que_cabe(trabalho: Duration, teto: u32) -> u32 {
    if trabalho.is_zero() {
        return teto;
    }
    let mut menor = teto;
    for taxa in TAXAS_OFERECIDAS.into_iter().filter(|&taxa| taxa <= teto) {
        menor = taxa;
        if trabalho * 2 <= Duration::from_secs(1) / taxa {
            return taxa;
        }
    }
    menor
}

#[cfg(test)]
mod testes {
    use super::*;

    const DE_SOBRA: Duration = Duration::from_micros(200);
    const APERTADO: Duration = Duration::from_millis(14);

    #[test]
    fn a_taxa_sai_do_trabalho_de_cada_quadro() {
        let casos = [
            (Duration::from_micros(940), 60, 60),
            (Duration::from_micros(4530), 60, 60),
            (Duration::from_micros(8333), 60, 60),
            (Duration::from_micros(8400), 60, 30),
            (Duration::from_millis(14), 60, 30),
            (Duration::from_millis(27), 60, 30),
            (Duration::from_millis(90), 60, 30),
            (Duration::from_micros(940), 30, 30),
            (Duration::from_millis(27), 30, 30),
            (Duration::ZERO, 60, 60),
        ];
        for (trabalho, teto, esperado) in casos {
            assert_eq!(taxa_que_cabe(trabalho, teto), esperado, "{trabalho:?} com teto {teto}");
        }
    }

    #[test]
    fn um_segundo_ruim_nao_derruba_a_taxa() {
        let mut cabe = RitmoQueCabe::novo(60);
        assert_eq!(cabe.segundo(APERTADO, 60), None);
        assert_eq!(cabe.segundo(APERTADO, 60), Some(30));
    }

    #[test]
    fn o_segundo_bom_limpa_o_aperto() {
        let mut cabe = RitmoQueCabe::novo(60);
        cabe.segundo(APERTADO, 60);
        assert_eq!(cabe.segundo(DE_SOBRA, 60), None);
        assert_eq!(cabe.segundo(APERTADO, 60), None);
    }

    #[test]
    fn subir_e_mais_lento_que_descer() {
        let mut cabe = RitmoQueCabe::novo(60);
        for _ in 1..SEGUNDOS_PARA_SUBIR {
            assert_eq!(cabe.segundo(DE_SOBRA, 30), None);
        }
        assert_eq!(cabe.segundo(DE_SOBRA, 30), Some(60));
    }

    #[test]
    fn o_teto_escolhido_pelo_dono_manda() {
        let mut cabe = RitmoQueCabe::novo(30);
        for _ in 0..SEGUNDOS_PARA_SUBIR + 5 {
            assert_eq!(cabe.segundo(DE_SOBRA, 30), None);
        }
    }

    #[test]
    fn o_piso_de_trinta_segura_maquina_lenta() {
        let mut cabe = RitmoQueCabe::novo(60);
        let mut atual = 60;
        for _ in 0..20 {
            if let Some(nova) = cabe.segundo(Duration::from_millis(90), atual) {
                atual = nova;
            }
        }
        assert_eq!(atual, 30);
    }
}
