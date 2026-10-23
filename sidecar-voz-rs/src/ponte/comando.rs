use serde::Deserialize;

const MARCA_DE_ORDEM: char = '\u{feff}';

#[derive(Debug, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Comando {
    pub cmd: String,
    pub url: Option<String>,
    pub token: Option<String>,
    pub par: Option<String>,
    pub tipo: Option<String>,
    pub dados: Option<String>,
    pub ligado: Option<bool>,
    pub eco: Option<bool>,
    pub ruido: Option<bool>,
    pub ganho: Option<bool>,
    pub monitor: Option<i32>,
    pub janela: Option<u64>,
    pub largura: Option<i32>,
    pub altura: Option<i32>,
    pub fps: Option<i32>,
    pub kbps: Option<i32>,
    pub duas_camadas: Option<bool>,
    pub volume: Option<i32>,
    pub sentido: Option<String>,
    pub id: Option<String>,
}

pub fn ler(linha: &str) -> Option<Result<Comando, String>> {
    let limpa = linha.trim_start_matches(MARCA_DE_ORDEM).trim();
    if limpa.is_empty() {
        return None;
    }
    Some(serde_json::from_str(limpa).map_err(|e| format!("comando ilegível: {e}")))
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn le_comando_com_marca_de_ordem() {
        let comando = ler("\u{feff}{\"cmd\":\"mudo\",\"ligado\":true}").unwrap().unwrap();
        assert_eq!(comando.cmd, "mudo");
        assert_eq!(comando.ligado, Some(true));
    }

    #[test]
    fn ignora_linha_vazia() {
        assert!(ler("   ").is_none());
    }

    #[test]
    fn le_duas_camadas_e_ignora_campo_desconhecido() {
        let comando = ler("{\"cmd\":\"transmitir\",\"duasCamadas\":true,\"novo\":1}").unwrap().unwrap();
        assert_eq!(comando.duas_camadas, Some(true));
    }

    #[test]
    fn avisa_comando_ilegivel() {
        assert!(ler("{isto não é json").unwrap().is_err());
    }
}
