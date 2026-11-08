use std::collections::HashMap;

pub const ALVO_DO_MICROFONE: &str = "meu-microfone";
pub const ALVO_DA_ESCUTA: &str = "minha-escuta";
const VOLUME_CHEIO: i32 = 100;

pub struct Controles {
    mudo: bool,
    surdo: bool,
    microfone: i32,
    escuta: i32,
    pessoas: HashMap<String, i32>,
    palco: String,
}

impl Default for Controles {
    fn default() -> Self {
        Self {
            mudo: false,
            surdo: false,
            microfone: VOLUME_CHEIO,
            escuta: VOLUME_CHEIO,
            pessoas: HashMap::new(),
            palco: String::new(),
        }
    }
}

impl Controles {
    pub fn palco(&self) -> &str {
        &self.palco
    }

    pub fn definir_palco(&mut self, quem: String) {
        self.palco = quem;
    }

    pub fn assiste_a(&self, quem: &str) -> bool {
        !self.palco.is_empty() && self.palco == quem
    }

    pub fn mudo(&self) -> bool {
        self.mudo
    }

    pub fn surdo(&self) -> bool {
        self.surdo
    }

    pub fn definir_mudo(&mut self, ligado: bool) {
        self.mudo = ligado;
    }

    pub fn definir_surdo(&mut self, ligado: bool) {
        self.surdo = ligado;
    }

    pub fn definir_volume(&mut self, par: &str, volume: i32) {
        let volume = volume.clamp(0, VOLUME_CHEIO);
        match par {
            ALVO_DO_MICROFONE => self.microfone = volume,
            ALVO_DA_ESCUTA => self.escuta = volume,
            pessoa => {
                self.pessoas.insert(pessoa.to_owned(), volume);
            }
        }
    }

    pub fn microfone_aberto(&self) -> bool {
        !self.mudo && self.microfone > 0
    }

    pub fn ouve(&self, pessoa: &str) -> bool {
        !self.surdo && self.escuta > 0 && self.volume_de(pessoa) > 0
    }

    fn volume_de(&self, pessoa: &str) -> i32 {
        self.pessoas.get(pessoa).copied().unwrap_or(VOLUME_CHEIO)
    }
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn de_partida_o_microfone_abre_e_todos_sao_ouvidos() {
        let controles = Controles::default();
        assert!(controles.microfone_aberto());
        assert!(controles.ouve("ana"));
    }

    #[test]
    fn mudo_ou_microfone_em_zero_fecham_o_microfone() {
        let mut controles = Controles::default();
        controles.definir_mudo(true);
        assert!(!controles.microfone_aberto());
        controles.definir_mudo(false);
        controles.definir_volume(ALVO_DO_MICROFONE, 0);
        assert!(!controles.microfone_aberto());
    }

    #[test]
    fn surdo_e_escuta_em_zero_calam_todos_e_volume_zero_cala_so_a_pessoa() {
        let mut controles = Controles::default();
        controles.definir_volume("ana", 0);
        assert!(!controles.ouve("ana"));
        assert!(controles.ouve("bia"));
        controles.definir_surdo(true);
        assert!(!controles.ouve("bia"));
        controles.definir_surdo(false);
        controles.definir_volume(ALVO_DA_ESCUTA, 0);
        assert!(!controles.ouve("bia"));
    }

    #[test]
    fn so_assiste_a_quem_esta_no_palco_e_palco_vazio_e_ninguem() {
        let mut controles = Controles::default();
        assert!(!controles.assiste_a("ana"), "de partida ninguém está no palco");
        controles.definir_palco("ana".into());
        assert!(controles.assiste_a("ana"));
        assert!(!controles.assiste_a("bia"));
        controles.definir_palco(String::new());
        assert!(!controles.assiste_a("ana"), "palco vazio não assina ninguém, ao contrário do Go");
        assert!(!controles.assiste_a(""));
    }

    #[test]
    fn volume_fora_da_faixa_fica_entre_zero_e_cem() {
        let mut controles = Controles::default();
        controles.definir_volume("ana", -5);
        assert!(!controles.ouve("ana"));
        controles.definir_volume("ana", 150);
        assert_eq!(controles.volume_de("ana"), VOLUME_CHEIO);
    }
}
