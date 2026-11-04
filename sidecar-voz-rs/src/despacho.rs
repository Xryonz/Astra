use crate::ponte::Comando;

pub enum Resultado {
    Seguir,
    Sair,
}

pub fn executar(comando: &Comando) -> Result<Resultado, String> {
    match comando.cmd.as_str() {
        "sair" => Ok(Resultado::Sair),
        "deixar" => Ok(Resultado::Seguir),
        outro => Err(format!("comando fora do protótipo: {outro}")),
    }
}
