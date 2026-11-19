use crate::aparelhos::{self, Sentido};
use crate::base64;
use crate::com::ComDaThread;
use crate::emissao::PedidoDeTela;
use crate::ponte::{Comando, Escritor, Evento};
use crate::sala::Sala;
use crate::tela;
use std::sync::Arc;

pub enum Resultado {
    Seguir,
    Sair,
}

pub async fn executar(comando: &Comando, sala: &mut Sala, escritor: &Arc<Escritor>) -> Result<Resultado, String> {
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
        "janelas" => {
            anunciar_as_janelas(escritor).await;
            Ok(Resultado::Seguir)
        }
        "usar" => {
            let sentido = Sentido::do_comando(comando.sentido.as_deref().unwrap_or_default());
            sala.usar(sentido, comando.id.clone().unwrap_or_default()).await;
            Ok(Resultado::Seguir)
        }
        "transmitir" => {
            match pedido_de_tela(comando)? {
                Some(pedido) => sala.transmitir(pedido).await?,
                None => sala.parar_a_tela().await,
            }
            Ok(Resultado::Seguir)
        }
        "assistir" => {
            sala.assistir(comando.par.clone().unwrap_or_default());
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

async fn anunciar_os_monitores(escritor: &Arc<Escritor>) {
    let lido = tokio::task::spawn_blocking(tela::listar_monitores).await.map_err(|e| e.to_string());
    let lista = match lido.and_then(|resultado| resultado) {
        Ok(lista) => lista,
        Err(msg) => {
            escritor.manda(&Evento::erro(format!("listar monitores: {msg}")));
            Vec::new()
        }
    };
    let monitores: Vec<u32> = lista.iter().map(|monitor| monitor.indice).collect();
    escritor.manda(&Evento::monitores(lista));
    let escritor = Arc::clone(escritor);
    tokio::task::spawn_blocking(move || mandar_as_miniaturas_dos_monitores(&monitores, &escritor));
}

async fn anunciar_as_janelas(escritor: &Arc<Escritor>) {
    let lista = tokio::task::spawn_blocking(tela::listar_janelas).await.unwrap_or_default();
    let janelas: Vec<u64> = lista.iter().map(|janela| janela.id).collect();
    escritor.manda(&Evento::janelas(lista));
    let escritor = Arc::clone(escritor);
    tokio::task::spawn_blocking(move || mandar_as_miniaturas_das_janelas(&janelas, &escritor));
}

fn mandar_as_miniaturas_dos_monitores(monitores: &[u32], escritor: &Escritor) {
    let _com = ComDaThread::iniciar();
    tela::miniaturas_dos_monitores(monitores, |monitor, png| {
        escritor.manda(&Evento::miniatura("monitor", monitor.to_string(), base64::codificar(&png)));
    });
}

fn mandar_as_miniaturas_das_janelas(janelas: &[u64], escritor: &Escritor) {
    let _com = ComDaThread::iniciar();
    tela::miniaturas_das_janelas(janelas, |janela, png| {
        escritor.manda(&Evento::miniatura("janela", janela.to_string(), base64::codificar(&png)));
    });
}

fn ligado(comando: &Comando) -> bool {
    comando.ligado.unwrap_or(false)
}

fn pedido_de_tela(comando: &Comando) -> Result<Option<PedidoDeTela>, String> {
    if !ligado(comando) {
        return Ok(None);
    }
    let positivo = |campo: Option<i32>| campo.and_then(|valor| u32::try_from(valor).ok()).filter(|&valor| valor > 0);
    match (positivo(comando.largura), positivo(comando.altura), positivo(comando.fps), positivo(comando.kbps)) {
        (Some(largura), Some(altura), Some(fps), Some(kbps)) => {
            let janela = comando.janela.filter(|&janela| janela != 0);
            let monitor = u32::try_from(comando.monitor.unwrap_or(0)).map_err(|_| "transmitir de monitor negativo")?;
            Ok(Some(PedidoDeTela { largura, altura, fps, kbps, janela, monitor }))
        }
        _ => Err("transmitir sem tamanho, ritmo ou taxa".into()),
    }
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

    fn comando_de_tela(ligado: bool, kbps: Option<i32>) -> Comando {
        Comando {
            cmd: "transmitir".into(),
            ligado: Some(ligado),
            largura: Some(1920),
            altura: Some(1080),
            fps: Some(60),
            kbps,
            ..Default::default()
        }
    }

    #[test]
    fn transmitir_desligado_para_e_ligado_completo_vira_pedido() {
        assert!(matches!(pedido_de_tela(&comando_de_tela(false, None)), Ok(None)));
        let Ok(Some(pedido)) = pedido_de_tela(&comando_de_tela(true, Some(8000))) else { panic!("pedido recusado") };
        assert_eq!((pedido.largura, pedido.altura, pedido.fps, pedido.kbps), (1920, 1080, 60, 8000));
    }

    #[test]
    fn transmitir_le_o_monitor_e_comeca_no_zero() {
        let Ok(Some(sem_monitor)) = pedido_de_tela(&comando_de_tela(true, Some(8000))) else { panic!("pedido recusado") };
        assert_eq!(sem_monitor.monitor, 0);
        let segundo = Comando { monitor: Some(1), ..comando_de_tela(true, Some(8000)) };
        assert!(matches!(pedido_de_tela(&segundo), Ok(Some(PedidoDeTela { monitor: 1, .. }))));
        let negativo = Comando { monitor: Some(-1), ..comando_de_tela(true, Some(8000)) };
        assert!(pedido_de_tela(&negativo).is_err());
    }

    #[test]
    fn transmitir_ligado_sem_taxa_ou_com_numero_negativo_recusa() {
        assert!(pedido_de_tela(&comando_de_tela(true, None)).is_err());
        assert!(pedido_de_tela(&comando_de_tela(true, Some(-1))).is_err());
    }

    #[test]
    fn sala_sem_credencial_ou_com_campo_vazio_recusa() {
        assert!(endereco_e_credencial(&comando_de_sala("wss://sala", "")).is_err());
        assert!(endereco_e_credencial(&Comando { cmd: "sala".into(), ..Default::default() }).is_err());
    }
}
