const ALFABETO: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

pub fn codificar(bytes: &[u8]) -> String {
    let mut texto = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for trio in bytes.chunks(3) {
        let numero = trio.iter().enumerate().fold(0u32, |junto, (i, &byte)| junto | u32::from(byte) << (16 - 8 * i));
        for posicao in 0..4 {
            if posicao <= trio.len() {
                let indice = (numero >> (18 - 6 * posicao)) & 0x3F;
                texto.push(char::from(ALFABETO[indice as usize]));
            } else {
                texto.push('=');
            }
        }
    }
    texto
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn os_exemplos_da_rfc_4648() {
        let casos = [
            ("", ""),
            ("f", "Zg=="),
            ("fo", "Zm8="),
            ("foo", "Zm9v"),
            ("foob", "Zm9vYg=="),
            ("fooba", "Zm9vYmE="),
            ("foobar", "Zm9vYmFy"),
        ];
        for (entrada, esperado) in casos {
            assert_eq!(codificar(entrada.as_bytes()), esperado, "{entrada:?}");
        }
    }

    #[test]
    fn bytes_altos_usam_mais_e_barra() {
        assert_eq!(codificar(&[0xFB, 0xFF, 0xFE]), "+//+");
    }
}
