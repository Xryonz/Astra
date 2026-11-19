use serde::Serialize;

const FATIA: u8 = 1;
const FATIA_IDR: u8 = 5;
const SPS: u8 = 7;
const PPS: u8 = 8;
const FATIA_B: u32 = 1;

#[derive(Clone, Copy, Debug, PartialEq, Serialize)]
pub struct Perfil {
    pub perfil: u8,
    pub restricoes: u8,
    pub nivel: u8,
}

#[derive(Debug, Default, PartialEq)]
pub struct Unidade {
    pub idr: bool,
    pub sps: bool,
    pub pps: bool,
    pub quadro_b: bool,
    pub perfil: Option<Perfil>,
}

pub fn ler_unidade(bytes: &[u8]) -> Unidade {
    let mut unidade = Unidade::default();
    for nal in Nals(bytes) {
        let Some((&cabeca, carga)) = nal.split_first() else { continue };
        match cabeca & 0x1F {
            SPS => {
                unidade.sps = true;
                unidade.perfil = unidade.perfil.or_else(|| perfil_do_sps(carga));
            }
            PPS => unidade.pps = true,
            tipo @ (FATIA | FATIA_IDR) => {
                unidade.idr = tipo == FATIA_IDR;
                unidade.quadro_b = tipo_da_fatia(carga) == Some(FATIA_B);
                break;
            }
            _ => {}
        }
    }
    unidade
}

fn perfil_do_sps(carga: &[u8]) -> Option<Perfil> {
    match carga {
        [perfil, restricoes, nivel, ..] => Some(Perfil { perfil: *perfil, restricoes: *restricoes, nivel: *nivel }),
        _ => None,
    }
}

fn tipo_da_fatia(carga: &[u8]) -> Option<u32> {
    let limpos = sem_prevencao(&carga[..carga.len().min(16)]);
    let mut bits = Bits { bytes: &limpos, posicao: 0 };
    bits.exp_golomb()?;
    bits.exp_golomb().map(|tipo| tipo % 5)
}

fn sem_prevencao(bytes: &[u8]) -> Vec<u8> {
    let mut limpos = Vec::with_capacity(bytes.len());
    let mut zeros = 0;
    for &byte in bytes {
        if zeros >= 2 && byte == 3 {
            zeros = 0;
            continue;
        }
        zeros = if byte == 0 { zeros + 1 } else { 0 };
        limpos.push(byte);
    }
    limpos
}

struct Bits<'a> {
    bytes: &'a [u8],
    posicao: usize,
}

impl Bits<'_> {
    fn bit(&mut self) -> Option<u32> {
        let byte = *self.bytes.get(self.posicao / 8)?;
        let bit = (byte >> (7 - self.posicao % 8)) & 1;
        self.posicao += 1;
        Some(u32::from(bit))
    }

    fn exp_golomb(&mut self) -> Option<u32> {
        let mut zeros = 0;
        while self.bit()? == 0 {
            zeros += 1;
            if zeros > 31 {
                return None;
            }
        }
        let mut resto = 0u32;
        for _ in 0..zeros {
            resto = (resto << 1) | self.bit()?;
        }
        Some((1u32 << zeros) - 1 + resto)
    }
}

struct Nals<'a>(&'a [u8]);

impl<'a> Iterator for Nals<'a> {
    type Item = &'a [u8];

    fn next(&mut self) -> Option<&'a [u8]> {
        let resto = &self.0[depois_do_inicio(self.0)?..];
        let fim = depois_do_inicio(resto).map_or(resto.len(), |seguinte| seguinte - 3);
        self.0 = &resto[fim..];
        Some(sem_zeros_no_fim(&resto[..fim]))
    }
}

fn depois_do_inicio(bytes: &[u8]) -> Option<usize> {
    bytes.windows(3).position(|janela| janela == [0, 0, 1]).map(|posicao| posicao + 3)
}

fn sem_zeros_no_fim(nal: &[u8]) -> &[u8] {
    let fim = nal.iter().rposition(|&byte| byte != 0).map_or(0, |ultimo| ultimo + 1);
    &nal[..fim]
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn idr_com_parametros_traz_perfil_e_nivel() {
        let unidade = ler_unidade(&[
            0, 0, 0, 1, 0x67, 77, 0x40, 42, 0xAC, 0, 0, 0, 1, 0x68, 0xEE, 0x3C, 0x80, 0, 0, 1, 0x65, 0x88, 0x84,
        ]);
        let perfil = Some(Perfil { perfil: 77, restricoes: 0x40, nivel: 42 });
        assert_eq!(unidade, Unidade { idr: true, sps: true, pps: true, quadro_b: false, perfil });
    }

    #[test]
    fn fatia_p_nao_e_idr_nem_b() {
        let unidade = ler_unidade(&[0, 0, 0, 1, 0x41, 0x9A, 0x00]);
        assert_eq!(unidade, Unidade::default());
    }

    #[test]
    fn fatia_b_e_reconhecida() {
        assert!(ler_unidade(&[0, 0, 1, 0x01, 0x9C]).quadro_b);
    }

    #[test]
    fn byte_de_prevencao_sai_antes_de_ler_os_bits() {
        assert_eq!(sem_prevencao(&[0, 0, 3, 1, 0, 0, 3]), vec![0, 0, 1, 0, 0]);
    }
}
