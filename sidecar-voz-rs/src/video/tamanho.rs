pub fn tamanho_de_saida(largura: u32, altura: u32, lado_menor: u32) -> (u32, u32) {
    if largura == 0 || altura == 0 {
        return (largura, altura);
    }
    let (menor, maior) = (f64::from(largura.min(altura)), f64::from(largura.max(altura)));
    let (lado_menor, lado_maior) = (f64::from(lado_menor), f64::from(lado_menor * 16 / 9));
    let encolhida = if menor > lado_menor { lado_menor / menor } else { 1.0 };
    let escala = if maior * encolhida > lado_maior { lado_maior / maior } else { encolhida };
    let par = |medida: u32| {
        let arredondada = (f64::from(medida) * escala + 0.5) as u32;
        (arredondada - arredondada % 2).max(2)
    };
    (par(largura), par(altura))
}

#[cfg(test)]
mod testes {
    use super::tamanho_de_saida;

    #[test]
    fn segue_a_tabela_do_go() {
        let casos = [
            ((1920, 1080), (1280, 720)),
            ((1080, 1920), (720, 1280)),
            ((3840, 2160), (1280, 720)),
            ((640, 360), (640, 360)),
            ((2560, 1080), (1280, 540)),
            ((1000, 721), (998, 720)),
        ];
        for ((largura, altura), esperado) in casos {
            assert_eq!(tamanho_de_saida(largura, altura, 720), esperado, "{largura}x{altura}");
        }
    }

    #[test]
    fn medida_vazia_volta_como_veio() {
        assert_eq!(tamanho_de_saida(0, 1080, 720), (0, 1080));
    }
}
