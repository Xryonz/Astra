use crate::aparelhos::{self, Sentido};
use crate::ponte::{Comando, Escritor, Evento};
use crate::sala::Sala;
use crate::tela;

pub enum Resultado {
    Seguir,
    Sair,
}

pub async fn executar(comando: &Comando, sala: &mut Sala, escritor: &Escritor) -> Result<Resultado, String> {
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
        "mudo" => {
            sala.mudar(ligado(comando)).await;
            Ok(Resultado::Seguir)
        }
        "surdo" => {
            sala.ensurdecer(ligado(comando)).await;
            Ok(Resultado::Seguir)
        }
        "volume" => {
            sala.ajustar_volume(comando.par.as_deref().unwrap_or_default(), comando.volume.unwrap_or_default());
            Ok(Resultado::Seguir)
        }
        "tratamento" => {
            relatar_o_tratamento(comando);
            Ok(Resultado::Seguir)
        }
        "aparelhos" => {
            anunciar_os_aparelhos(escritor).await?;
            Ok(Resultado::Seguir)
        }
        "monitores" => {
            anunciar_os_monitores(escritor).await;
            Ok(Resultado::Seguir)
        }
        "usar" => {
            let sentido = Sentido::do_comando(comando.sentido.as_deref().unwrap_or_default());
            sala.usar(sentido, comando.id.clone().unwrap_or_default()).await;
            Ok(Resultado::Seguir)
        }
        "sair" => Ok(Resultado::Sair),
        outro => Err(format!("comando fora do protótipo: {outro}")),
    }
}

async fn anunciar_os_aparelhos(escritor: &Escritor) -> Result<(), String> {
    for sentido in [Sentido::Entrada, Sentido::Saida] {
        let lista = tokio::task::spawn_blocking(move || aparelhos::listar(sentido))
            .await
            .map_err(|e| format!("listar aparelhos de {}: {e}", sentido.nome()))?
            .map_err(|e| format!("listar aparelhos de {}: {e}", sentido.nome()))?;
        escritor.manda(&Evento::aparelhos(sentido.nome(), lista));
    }
    Ok(())
}

async fn anunciar_os_monitores(escritor: &Escritor) {
    let lido = tokio::task::spawn_blocking(tela::listar_monitores).await.map_err(|e| e.to_string());
    let lista = match lido.and_then(|resultado| resultado) {
        Ok(lista) => lista,
        Err(msg) => {
            escritor.manda(&Evento::erro(format!("listar monitores: {msg}")));
            Vec::new()
        }
    };
    escritor.manda(&Evento::monitores(lista));
}

fn ligado(comando: &Comando) -> bool {
    comando.ligado.unwrap_or(false)
}

fn relatar_o_tratamento(comando: &Comando) {
    eprintln!(
        "tratamento pedido: eco {}, ruído {}, ganho {}; no protótipo os três ficam ligados",
        comando.eco.unwrap_or(false),
        comando.ruido.unwrap_or(false),
        comando.ganho.unwrap_or(false)
    );
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
