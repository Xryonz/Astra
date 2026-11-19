use std::time::{Duration, Instant};

const QUADROS_QUE_MEDEM_O_TRABALHO: u32 = 16;

pub struct Ritmo {
    comeco: Instant,
    quadros: u32,
    bytes: usize,
    chaves: u32,
    chaves_pedidas: u32,
    alvo_da_rede: Option<u32>,
    latencia: Duration,
    trabalho: Duration,
    reapresentados: u32,
    janela: Option<(u32, u32)>,
}

pub struct Segundo {
    pub resumo: String,
    pub trabalho_por_quadro: Option<Duration>,
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
            trabalho: Duration::ZERO,
            reapresentados: 0,
            janela: None,
        }
    }

    pub fn contar_quadro(&mut self, bytes: usize, chave: bool, latencia: Option<Duration>) {
        self.quadros += 1;
        self.bytes += bytes;
        self.chaves += u32::from(chave);
        self.latencia += latencia.unwrap_or_default();
    }

    pub fn contar_trabalho(&mut self, gasto: Duration) {
        self.trabalho += gasto;
    }

    pub fn contar_reapresentado(&mut self) {
        self.reapresentados += 1;
    }

    pub fn anotar_janela(&mut self, largura: u32, altura: u32) {
        self.janela = Some((largura, altura));
    }

    pub fn contar_pedido_de_chave(&mut self) {
        self.chaves_pedidas += 1;
    }

    pub fn anotar_alvo_da_rede(&mut self, kbps: u32) {
        self.alvo_da_rede = Some(kbps);
    }

    pub fn fechar_o_segundo(&mut self, kbps: u32) -> Option<Segundo> {
        let passou = self.comeco.elapsed();
        if passou < Duration::from_secs(1) {
            return None;
        }
        let segundo = Segundo {
            resumo: self.resumo(passou, kbps),
            trabalho_por_quadro: (self.quadros >= QUADROS_QUE_MEDEM_O_TRABALHO).then(|| self.trabalho / self.quadros),
        };
        *self = Self::novo();
        Some(segundo)
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
            let quadros = f64::from(self.quadros);
            let latencia = self.latencia.as_secs_f64() * 1000.0 / quadros;
            let trabalho = self.trabalho.as_secs_f64() * 1000.0 / quadros;
            texto.push_str(&format!(" · codificador {latencia:.1} ms por quadro · trabalho {trabalho:.1} ms por quadro"));
        }
        if self.reapresentados > 0 {
            texto.push_str(&format!(" · {} reapresentados com a tela parada", self.reapresentados));
        }
        if let Some((largura, altura)) = self.janela {
            texto.push_str(&format!(" · janela agora {largura}x{altura}"));
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
            ritmo.contar_trabalho(Duration::from_millis(1));
        }
        ritmo.contar_pedido_de_chave();
        ritmo.contar_reapresentado();
        ritmo.anotar_alvo_da_rede(4000);
        assert_eq!(
            ritmo.resumo(Duration::from_secs(1), 8000),
            "60 fps · 8.0 de 8.0 Mbps · 1 quadros-chave · 1 pedidos de chave · rede pede 4000 kbps · codificador 5.0 ms por quadro · trabalho 1.0 ms por quadro · 1 reapresentados com a tela parada"
        );
    }

    #[test]
    fn o_trabalho_so_vale_com_quadros_suficientes() {
        let mut poucos = Ritmo { comeco: Instant::now() - Duration::from_secs(1), ..Ritmo::novo() };
        for _ in 1..QUADROS_QUE_MEDEM_O_TRABALHO {
            poucos.contar_quadro(100, false, None);
            poucos.contar_trabalho(Duration::from_millis(2));
        }
        assert_eq!(poucos.fechar_o_segundo(8000).and_then(|segundo| segundo.trabalho_por_quadro), None);

        let mut bastantes = Ritmo { comeco: Instant::now() - Duration::from_secs(1), ..Ritmo::novo() };
        for _ in 0..QUADROS_QUE_MEDEM_O_TRABALHO {
            bastantes.contar_quadro(100, false, None);
            bastantes.contar_trabalho(Duration::from_millis(2));
        }
        let medido = bastantes.fechar_o_segundo(8000).and_then(|segundo| segundo.trabalho_por_quadro);
        assert_eq!(medido, Some(Duration::from_millis(2)));
    }
}
