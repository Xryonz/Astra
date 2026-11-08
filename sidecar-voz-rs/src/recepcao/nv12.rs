use crate::entrega::Quadro;
use livekit::webrtc::native::yuv_helper::i420_to_nv12;
use livekit::webrtc::video_frame::{I420Buffer, VideoBuffer};

pub fn nv12_do_quadro(buffer: &dyn VideoBuffer, reservar: impl FnOnce(usize) -> Vec<u8>) -> Option<Quadro> {
    buffer.as_i420().map(|i420| converter(i420, reservar))
}

fn converter(i420: &I420Buffer, reservar: impl FnOnce(usize) -> Vec<u8>) -> Quadro {
    let (largura, altura) = (i420.width(), i420.height());
    let passo = largura.next_multiple_of(2);
    let tamanho_do_y = (passo * altura) as usize;
    let mut dados = reservar(tamanho_do_y + (passo * altura.div_ceil(2)) as usize);
    let (y, u, v) = i420.data();
    let (passo_do_y, passo_do_u, passo_do_v) = i420.strides();
    let (destino_y, destino_uv) = dados.split_at_mut(tamanho_do_y);
    i420_to_nv12(
        y,
        passo_do_y,
        u,
        passo_do_u,
        v,
        passo_do_v,
        destino_y,
        passo,
        destino_uv,
        passo,
        largura as i32,
        altura as i32,
    );
    Quadro { dados, largura, altura, passo }
}

#[cfg(test)]
mod testes {
    use super::*;

    fn i420_pintado(largura: u32, altura: u32) -> I420Buffer {
        let mut i420 = I420Buffer::new(largura, altura);
        let (y, u, v) = i420.data_mut();
        y.iter_mut().enumerate().for_each(|(i, valor)| *valor = 10 + i as u8);
        u.iter_mut().enumerate().for_each(|(i, valor)| *valor = 100 + i as u8);
        v.iter_mut().enumerate().for_each(|(i, valor)| *valor = 200 + i as u8);
        i420
    }

    #[test]
    fn o_y_vem_inteiro_e_o_uv_intercalado_logo_depois() {
        let quadro = nv12_do_quadro(&i420_pintado(4, 2), |tamanho| vec![0; tamanho]).unwrap();
        assert_eq!((quadro.largura, quadro.altura, quadro.passo), (4, 2, 4));
        assert_eq!(quadro.dados, [10, 11, 12, 13, 14, 15, 16, 17, 100, 200, 101, 201]);
    }

    #[test]
    fn largura_e_altura_impares_arredondam_o_passo_e_as_linhas_do_uv() {
        let quadro = nv12_do_quadro(&i420_pintado(3, 3), |tamanho| vec![0; tamanho]).unwrap();
        assert_eq!(quadro.passo, 4);
        assert_eq!(quadro.dados.len(), 4 * 3 + 4 * 2);
    }
}
