use crate::aparelhos::Aparelho;
use crate::tela::{Janela, Monitor};
use serde::Serialize;

const ESTADO: &str = "estado";
const TRANSMISSAO: &str = "transmissao";

#[derive(Debug, Default, Serialize)]
pub struct Evento {
    pub ev: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub par: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tipo: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub dados: Option<String>,
    #[serde(rename = "v", skip_serializing_if = "Option::is_none")]
    pub valor: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub msg: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub aparelhos: Option<Vec<Aparelho>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub monitores: Option<Vec<Monitor>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub janelas: Option<Vec<Janela>>,
}

impl Evento {
    pub fn pronto() -> Self {
        Self { ev: "pronto", ..Default::default() }
    }

    pub fn erro(msg: impl Into<String>) -> Self {
        Self { ev: "erro", msg: Some(msg.into()), ..Default::default() }
    }

    pub fn erro_do_comando(par: Option<String>, msg: impl Into<String>) -> Self {
        Self { par: par.filter(|p| !p.is_empty()), ..Self::erro(msg) }
    }

    pub fn na_sala() -> Self {
        Self::de_estado(None, "connected")
    }

    pub fn chegou(par: String) -> Self {
        Self::de_estado(Some(par), "connected")
    }

    pub fn saiu(par: String) -> Self {
        Self::de_estado(Some(par), "closed")
    }

    pub fn caiu_da_sala(tipo: &str, msg: &str) -> Self {
        Self { tipo: Some(tipo.into()), msg: Some(msg.into()), ..Self::de_estado(None, "disconnected") }
    }

    pub fn aparelhos(tipo: &str, lista: Vec<Aparelho>) -> Self {
        Self {
            ev: "aparelhos",
            tipo: Some(tipo.into()),
            aparelhos: Some(lista).filter(|lista| !lista.is_empty()),
            ..Default::default()
        }
    }

    pub fn monitores(lista: Vec<Monitor>) -> Self {
        Self { ev: "monitores", monitores: Some(lista).filter(|lista| !lista.is_empty()), ..Default::default() }
    }

    pub fn janelas(lista: Vec<Janela>) -> Self {
        Self { ev: "janelas", janelas: Some(lista).filter(|lista| !lista.is_empty()), ..Default::default() }
    }

    pub fn miniatura(tipo: &str, par: String, dados: String) -> Self {
        Self { ev: "miniatura", par: Some(par), tipo: Some(tipo.into()), dados: Some(dados), ..Default::default() }
    }

    pub fn fala(par: &str, ligado: bool) -> Self {
        Self {
            ev: "fala",
            par: Some(par.to_owned()).filter(|p| !p.is_empty()),
            valor: Some(um_ou_zero(ligado).into()),
            ..Default::default()
        }
    }

    pub fn estado_da_voz(par: &str, tipo: &str, ligado: bool) -> Self {
        Self {
            ev: "estado-da-voz",
            par: Some(par.into()),
            tipo: Some(tipo.into()),
            valor: Some(um_ou_zero(ligado).into()),
            ..Default::default()
        }
    }

    pub fn transmissao(tipo: &str, msg: String) -> Self {
        Self {
            ev: TRANSMISSAO,
            tipo: Some(tipo.into()),
            valor: Some(um_ou_zero(true).into()),
            msg: Some(msg),
            ..Default::default()
        }
    }

    pub fn transmissao_encerrada() -> Self {
        Self { ev: TRANSMISSAO, valor: Some(um_ou_zero(false).into()), ..Default::default() }
    }

    fn de_estado(par: Option<String>, valor: &str) -> Self {
        Self { ev: ESTADO, par, valor: Some(valor.into()), ..Default::default() }
    }
}

fn um_ou_zero(ligado: bool) -> &'static str {
    if ligado { "1" } else { "0" }
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn pronto_sai_so_com_o_nome() {
        assert_eq!(serde_json::to_string(&Evento::pronto()).unwrap(), "{\"ev\":\"pronto\"}");
    }

    #[test]
    fn erro_do_comando_leva_o_par_e_omite_par_vazio() {
        let com_par = serde_json::to_string(&Evento::erro_do_comando(Some("ana".into()), "falhou")).unwrap();
        assert_eq!(com_par, "{\"ev\":\"erro\",\"par\":\"ana\",\"msg\":\"falhou\"}");
        let sem_par = serde_json::to_string(&Evento::erro_do_comando(Some(String::new()), "falhou")).unwrap();
        assert_eq!(sem_par, "{\"ev\":\"erro\",\"msg\":\"falhou\"}");
    }

    #[test]
    fn estado_da_sala_sai_sem_par_e_o_de_alguem_leva_o_par() {
        assert_eq!(serde_json::to_string(&Evento::na_sala()).unwrap(), "{\"ev\":\"estado\",\"v\":\"connected\"}");
        assert_eq!(
            serde_json::to_string(&Evento::chegou("ana".into())).unwrap(),
            "{\"ev\":\"estado\",\"par\":\"ana\",\"v\":\"connected\"}"
        );
        assert_eq!(
            serde_json::to_string(&Evento::saiu("ana".into())).unwrap(),
            "{\"ev\":\"estado\",\"par\":\"ana\",\"v\":\"closed\"}"
        );
    }

    #[test]
    fn aparelhos_saem_como_no_go_e_lista_vazia_e_omitida() {
        let fone = Aparelho { id: "{0.0.1.00000000}.{abc}".into(), nome: "Fone".into() };
        assert_eq!(
            serde_json::to_string(&Evento::aparelhos("entrada", vec![fone])).unwrap(),
            "{\"ev\":\"aparelhos\",\"tipo\":\"entrada\",\"aparelhos\":[{\"id\":\"{0.0.1.00000000}.{abc}\",\"nome\":\"Fone\"}]}"
        );
        assert_eq!(
            serde_json::to_string(&Evento::aparelhos("saida", Vec::new())).unwrap(),
            "{\"ev\":\"aparelhos\",\"tipo\":\"saida\"}"
        );
    }

    #[test]
    fn monitores_saem_como_no_go_e_lista_vazia_e_omitida() {
        let tela = Monitor { indice: 0, nome: "\\\\.\\DISPLAY1".into(), largura: 1920, altura: 1080, principal: true };
        assert_eq!(
            serde_json::to_string(&Evento::monitores(vec![tela])).unwrap(),
            "{\"ev\":\"monitores\",\"monitores\":[{\"indice\":0,\"nome\":\"\\\\\\\\.\\\\DISPLAY1\",\"largura\":1920,\"altura\":1080,\"principal\":true}]}"
        );
        assert_eq!(serde_json::to_string(&Evento::monitores(Vec::new())).unwrap(), "{\"ev\":\"monitores\"}");
    }

    #[test]
    fn janelas_saem_como_no_go_e_lista_vazia_e_omitida() {
        let janela = Janela { id: 132458, nome: "Bloco de Notas".into(), largura: 800, altura: 600 };
        assert_eq!(
            serde_json::to_string(&Evento::janelas(vec![janela])).unwrap(),
            "{\"ev\":\"janelas\",\"janelas\":[{\"id\":132458,\"nome\":\"Bloco de Notas\",\"largura\":800,\"altura\":600}]}"
        );
        assert_eq!(serde_json::to_string(&Evento::janelas(Vec::new())).unwrap(), "{\"ev\":\"janelas\"}");
    }

    #[test]
    fn miniatura_sai_com_os_campos_na_ordem_do_go() {
        assert_eq!(
            serde_json::to_string(&Evento::miniatura("janela", "132458".into(), "iVBO".into())).unwrap(),
            "{\"ev\":\"miniatura\",\"par\":\"132458\",\"tipo\":\"janela\",\"dados\":\"iVBO\"}"
        );
    }

    #[test]
    fn fala_propria_sai_sem_par_e_a_dos_outros_com_par() {
        assert_eq!(serde_json::to_string(&Evento::fala("", true)).unwrap(), "{\"ev\":\"fala\",\"v\":\"1\"}");
        assert_eq!(
            serde_json::to_string(&Evento::fala("ana", false)).unwrap(),
            "{\"ev\":\"fala\",\"par\":\"ana\",\"v\":\"0\"}"
        );
    }

    #[test]
    fn estado_da_voz_sai_como_no_go() {
        assert_eq!(
            serde_json::to_string(&Evento::estado_da_voz("ana", "mudo", true)).unwrap(),
            "{\"ev\":\"estado-da-voz\",\"par\":\"ana\",\"tipo\":\"mudo\",\"v\":\"1\"}"
        );
        assert_eq!(
            serde_json::to_string(&Evento::estado_da_voz("ana", "surdo", false)).unwrap(),
            "{\"ev\":\"estado-da-voz\",\"par\":\"ana\",\"tipo\":\"surdo\",\"v\":\"0\"}"
        );
    }

    #[test]
    fn transmissao_sai_como_no_go() {
        assert_eq!(
            serde_json::to_string(&Evento::transmissao("ritmo", "60 fps".into())).unwrap(),
            "{\"ev\":\"transmissao\",\"tipo\":\"ritmo\",\"v\":\"1\",\"msg\":\"60 fps\"}"
        );
        assert_eq!(
            serde_json::to_string(&Evento::transmissao_encerrada()).unwrap(),
            "{\"ev\":\"transmissao\",\"v\":\"0\"}"
        );
    }

    #[test]
    fn queda_leva_tipo_e_motivo() {
        assert_eq!(
            serde_json::to_string(&Evento::caiu_da_sala("queda", "SIGNAL_CLOSE")).unwrap(),
            "{\"ev\":\"estado\",\"tipo\":\"queda\",\"v\":\"disconnected\",\"msg\":\"SIGNAL_CLOSE\"}"
        );
    }
}
