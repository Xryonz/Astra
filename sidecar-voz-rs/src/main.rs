mod aparelhos;
mod base64;
mod com;
mod despacho;
mod emissao;
mod entrega;
mod ferramentas;
mod ponte;
mod recepcao;
mod registro_do_webrtc;
mod sala;
mod tela;
mod vigilia;

use despacho::Resultado;
use ponte::{Escritor, Evento};
use sala::Sala;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::sync::mpsc;

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() {
    if let Some(codigo) = ferramentas::rodar_se_pedida() {
        std::process::exit(codigo);
    }
    registro_do_webrtc::ligar();
    let escritor = Arc::new(Escritor::novo());
    let (avisar, mut mudancas) = mpsc::unbounded_channel();
    let _vigia = aparelhos::vigiar(avisar).inspect_err(|e| eprintln!("sem vigia de aparelhos: {e}")).ok();
    escritor.manda(&Evento::pronto());
    let mut sala = Sala::nova(Arc::clone(&escritor), entrega::Entrega::do_ambiente());

    let mut linhas = BufReader::new(tokio::io::stdin()).lines();
    loop {
        tokio::select! {
            lida = linhas.next_line() => {
                let Ok(Some(linha)) = lida else { break };
                if let Resultado::Sair = atender(&linha, &mut sala, &escritor).await {
                    break;
                }
            }
            Some(()) = mudancas.recv() => {
                while mudancas.try_recv().is_ok() {}
                sala.reconciliar_aparelhos().await;
            }
        }
    }
    sala.deixar().await;
}

async fn atender(linha: &str, sala: &mut Sala, escritor: &Arc<Escritor>) -> Resultado {
    let Some(lido) = ponte::ler(linha) else { return Resultado::Seguir };
    let comando = match lido {
        Ok(comando) => comando,
        Err(msg) => {
            escritor.manda(&Evento::erro(msg));
            return Resultado::Seguir;
        }
    };
    match despacho::executar(&comando, sala, escritor).await {
        Ok(resultado) => resultado,
        Err(msg) => {
            escritor.manda(&Evento::erro_do_comando(comando.par.clone(), msg));
            Resultado::Seguir
        }
    }
}
