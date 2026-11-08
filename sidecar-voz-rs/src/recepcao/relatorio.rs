use std::time::{Duration, Instant};

const JANELA: Duration = Duration::from_secs(1);

pub struct Relatorio {
    comeco: Instant,
    quadros: u32,
    conversao: Duration,
    tamanho: (u32, u32),
}

impl Relatorio {
    pub fn novo(agora: Instant) -> Self {
        Self { comeco: agora, quadros: 0, conversao: Duration::ZERO, tamanho: (0, 0) }
    }

    pub fn contar(&mut self, tamanho: (u32, u32), conversao: Duration) {
        self.quadros += 1;
        self.conversao += conversao;
        self.tamanho = tamanho;
    }

    pub fn fechar_o_segundo(&mut self, agora: Instant) -> Option<String> {
        let passou = agora.saturating_duration_since(self.comeco);
        if passou < JANELA {
            return None;
        }
        let quadros = f64::from(self.quadros);
        let por_segundo = (quadros / passou.as_secs_f64()).round();
        let conversao = if self.quadros > 0 { self.conversao.as_secs_f64() * 1000.0 / quadros } else { 0.0 };
        let (largura, altura) = self.tamanho;
        *self = Self { tamanho: self.tamanho, ..Self::novo(agora) };
        Some(format!("{por_segundo} fps · {largura}x{altura} · conversão {conversao:.1} ms por quadro"))
    }
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn so_fecha_depois_de_um_segundo_e_recomeca_a_conta() {
        let comeco = Instant::now();
        let mut relatorio = Relatorio::novo(comeco);
        for _ in 0..60 {
            relatorio.contar((1280, 720), Duration::from_micros(500));
        }
        assert_eq!(relatorio.fechar_o_segundo(comeco + Duration::from_millis(900)), None);
        assert_eq!(
            relatorio.fechar_o_segundo(comeco + JANELA).as_deref(),
            Some("60 fps · 1280x720 · conversão 0.5 ms por quadro")
        );
        assert_eq!(relatorio.quadros, 0);
        assert_eq!(relatorio.tamanho, (1280, 720), "o tamanho fica para o segundo seguinte");
    }

    #[test]
    fn segundo_sem_quadro_relata_zero() {
        let comeco = Instant::now();
        let mut relatorio = Relatorio::novo(comeco);
        assert_eq!(
            relatorio.fechar_o_segundo(comeco + JANELA).as_deref(),
            Some("0 fps · 0x0 · conversão 0.0 ms por quadro")
        );
    }
}
