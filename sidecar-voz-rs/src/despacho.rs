use crate::ponte::Comando;
use crate::sala::Sala;

pub enum Resultado {
    Seguir,
    Sair,
}

pub async fn executar(comando: &Comando, sala: &mut Sala) -> Result<Resultado, String> {
    match comando.cmd.as_str() {
        "sala" => {
            let (url, token) = endereco_e_credencial(comando)?;
            sala.entrar(url, token).await?;
            Ok(Resultado::Seguir)
        }
        "deixar" => {
            sala.deixar().await;
            Ok(Resultado::Seguir)
        }
        "sair" => Ok(Resultado::Sair),
        outro => Err(format!("comando fora do protótipo: {outro}")),
    }
}

fn endereco_e_credencial(comando: &Comando) -> Result<(&str, &str), String> {
    match (preenchido(&comando.url), preenchido(&comando.token)) {
        (Some(url), Some(token)) => Ok((url, token)),
        _ => Err("sala sem endereço ou credencial".into()),
    }
}

fn preenchido(campo: &Option<String>) -> Option<&str> {
    campo.as_deref().filter(|valor| !valor.is_empty())
}

#[cfg(test)]
mod testes {
    use super::*;

    fn comando_de_sala(url: &str, token: &str) -> Comando {
        Comando { cmd: "sala".into(), url: Some(url.into()), token: Some(token.into()), ..Default::default() }
    }

    #[test]
    fn sala_com_endereco_e_credencial_passa() {
        let comando = comando_de_sala("wss://sala", "abc");
        assert_eq!(endereco_e_credencial(&comando), Ok(("wss://sala", "abc")));
    }

    #[test]
    fn sala_sem_credencial_ou_com_campo_vazio_recusa() {
        assert!(endereco_e_credencial(&comando_de_sala("wss://sala", "")).is_err());
        assert!(endereco_e_credencial(&Comando { cmd: "sala".into(), ..Default::default() }).is_err());
    }
}
