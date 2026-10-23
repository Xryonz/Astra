mod despacho;
mod ponte;

use despacho::Resultado;
use ponte::{Escritor, Evento};
use tokio::io::{AsyncBufReadExt, BufReader};

#[tokio::main]
async fn main() {
    let escritor = Escritor::novo();
    escritor.manda(&Evento::pronto());

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
        match despacho::executar(&comando) {
            Ok(Resultado::Sair) => break,
            Ok(Resultado::Seguir) => {}
            Err(msg) => escritor.manda(&Evento::erro_do_comando(comando.par.clone(), msg)),
        }
    }
}
