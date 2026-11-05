mod despacho;
mod ponte;
mod sala;

use despacho::Resultado;
use ponte::{Escritor, Evento};
use sala::Sala;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, BufReader};

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() {
    let escritor = Arc::new(Escritor::novo());
    escritor.manda(&Evento::pronto());
    let mut sala = Sala::nova(Arc::clone(&escritor));

    let mut linhas = BufReader::new(tokio::io::stdin()).lines();
    while let Ok(Some(linha)) = linhas.next_line().await {
        let Some(lido) = ponte::ler(&linha) else { continue };
        let comando = match lido {
            Ok(comando) => comando,
            Err(msg) => {
                escritor.manda(&Evento::erro(msg));
                continue;
            }
        };
        match despacho::executar(&comando, &mut sala).await {
            Ok(Resultado::Sair) => break,
            Ok(Resultado::Seguir) => {}
            Err(msg) => escritor.manda(&Evento::erro_do_comando(comando.par.clone(), msg)),
        }
    }
    sala.deixar().await;
}
