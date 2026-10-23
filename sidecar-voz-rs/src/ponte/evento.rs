use serde::Serialize;

#[derive(Debug, Default, Serialize)]
pub struct Evento {
    pub ev: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub par: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tipo: Option<String>,
    #[serde(rename = "v", skip_serializing_if = "Option::is_none")]
    pub valor: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub msg: Option<String>,
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
}
