use super::leitor::{abrir_leitor, duracao_em_100ns, fluxos_do_arquivo, tipo_nativo};
use serde::Serialize;
use windows::Win32::Media::MediaFoundation::MF_MT_FRAME_SIZE;

pub const SEM_IMAGEM: &str = "o arquivo não tem imagem de vídeo";

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Ficha {
    pub duracao_ms: u64,
    pub largura: u32,
    pub altura: u32,
    pub tem_som: bool,
}

pub fn sondar(caminho: &str) -> Result<Ficha, String> {
    let leitor = abrir_leitor(caminho, false)?;
    let fluxos = fluxos_do_arquivo(&leitor);
    let video = fluxos.video.ok_or(SEM_IMAGEM)?;
    let nativo = tipo_nativo(&leitor, video)?;
    let tamanho = unsafe { nativo.GetUINT64(&MF_MT_FRAME_SIZE) }.unwrap_or(0);
    Ok(Ficha {
        duracao_ms: duracao_em_100ns(&leitor) / 10_000,
        largura: (tamanho >> 32) as u32,
        altura: tamanho as u32,
        tem_som: fluxos.som.is_some(),
    })
}
