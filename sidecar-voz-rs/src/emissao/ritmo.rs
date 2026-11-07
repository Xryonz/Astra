use std::time::{Duration, Instant};

pub struct Ritmo {
    comeco: Instant,
    quadros: u32,
    bytes: usize,
    chaves: u32,
    chaves_pedidas: u32,
    alvo_da_rede: Option<u32>,
    latencia: Duration,
}

impl Ritmo {
    pub fn novo() -> Self {
        Self {
            comeco: Instant::now(),
            quadros: 0,
            bytes: 0,
            chaves: 0,
            chaves_pedidas: 0,
            alvo_da_rede: None,
            latencia: Duration::ZERO,
        }
    }

    pub fn contar_quadro(&mut self, bytes: usize, chave: bool, latencia: Option<Duration>) {
        self.quadros += 1;
        self.bytes += bytes;
        self.chaves += u32::from(chave);
        self.latencia += latencia.unwrap_or_default();
    }

    pub fn contar_pedido_de_chave(&mut self) {
        self.chaves_pedidas += 1;
    }

    pub fn anotar_alvo_da_rede(&mut self, kbps: u32) {
        self.alvo_da_rede = Some(kbps);
    }

    pub fn fechar_o_segundo(&mut self, kbps: u32) -> Option<String> {
        let passou = self.comeco.elapsed();
        if passou < Duration::from_secs(1) {
            return None;
        }
        let resumo = self.resumo(passou, kbps);
        *self = Self::novo();
        Some(resumo)
    }

    fn resumo(&self, passou: Duration, kbps: u32) -> String {
        let segundos = passou.as_secs_f64();
        let mut texto = format!(
            "{} fps · {:.1} de {:.1} Mbps · {} quadros-chave · {} pedidos de chave",
            (f64::from(self.quadros) / segundos).round(),
            self.bytes as f64 * 8.0 / segundos / 1e6,
            f64::from(kbps) / 1000.0,
            self.chaves,
            self.chaves_pedidas
        );
        if let Some(alvo) = self.alvo_da_rede {
            texto.push_str(&format!(" · rede pede {alvo} kbps"));
        }
        if self.quadros > 0 {
            let por_quadro = self.latencia.as_secs_f64() * 1000.0 / f64::from(self.quadros);
            texto.push_str(&format!(" · codificador {por_quadro:.1} ms por quadro"));
        }
        texto
    }
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn resumo_do_segundo_conta_quadros_banda_chaves_e_pedido_da_rede() {
        let mut ritmo = Ritmo::novo();
        for quadro in 0..60 {
            ritmo.contar_quadro(1_000_000 / 60, quadro == 0, Some(Duration::from_millis(5)));
        }
        ritmo.contar_pedido_de_chave();
        ritmo.anotar_alvo_da_rede(4000);
        assert_eq!(
            ritmo.resumo(Duration::from_secs(1), 8000),
            "60 fps · 8.0 de 8.0 Mbps · 1 quadros-chave · 1 pedidos de chave · rede pede 4000 kbps · codificador 5.0 ms por quadro"
        );
    }
}
