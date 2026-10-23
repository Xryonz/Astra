use super::evento::Evento;
use std::io::{Stdout, Write};
use std::sync::Mutex;

pub struct Escritor {
    saida: Mutex<Stdout>,
}

impl Escritor {
    pub fn novo() -> Self {
        Self { saida: Mutex::new(std::io::stdout()) }
    }

    pub fn manda(&self, evento: &Evento) {
        let Ok(linha) = serde_json::to_string(evento) else { return };
        let Ok(mut saida) = self.saida.lock() else { return };
        if writeln!(saida, "{linha}").and_then(|_| saida.flush()).is_err() {
            eprintln!("ponte fechou na escrita");
        }
    }
}
