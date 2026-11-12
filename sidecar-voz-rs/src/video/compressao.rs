use super::copia::{Andamento, copiar_amostras};
use super::ficha::SEM_IMAGEM;
use super::gravador::{abrir_gravador, somar_fluxo};
use super::leitor::{abrir_leitor, duracao_em_100ns, fluxos_do_arquivo, tipo_nativo};
use super::tamanho::tamanho_de_saida;
use super::tipos::{Imagem, Som, tipo_aac, tipo_h264, tipo_nv12, tipo_pcm};
use crate::tela::em;
use std::collections::HashMap;
use windows::Win32::Media::MediaFoundation::{
    IMFMediaType, IMFSinkWriter, IMFSourceReader, MF_MT_AUDIO_NUM_CHANNELS, MF_MT_AUDIO_SAMPLES_PER_SECOND,
    MF_MT_FRAME_RATE, MF_MT_FRAME_SIZE, MF_MT_VIDEO_ROTATION, MF_SOURCE_READER_ALL_STREAMS,
};

const TODOS_OS_FLUXOS: u32 = MF_SOURCE_READER_ALL_STREAMS.0 as u32;
const TAXA_QUANDO_FALTA: u64 = (30 << 32) | 1;

pub struct Ajustes {
    pub lado_menor: u32,
    pub kbps: u32,
    pub kbps_do_som: u32,
}

struct Original {
    imagem: Imagem,
    rotacao: Option<u32>,
}

pub fn comprimir(entrada: &str, saida: &str, ajustes: &Ajustes, avisar: impl FnMut(f64)) -> Result<(), String> {
    let leitor = abrir_leitor(entrada, true)?;
    let fluxos = fluxos_do_arquivo(&leitor);
    let video = fluxos.video.ok_or(SEM_IMAGEM)?;
    let duracao = duracao_em_100ns(&leitor);
    let original = ler_o_original(&tipo_nativo(&leitor, video)?, ajustes.lado_menor);
    preparar_a_imagem(&leitor, video, original.imagem)?;
    let som = match fluxos.som {
        Some(fluxo) => Some((fluxo, preparar_o_som(&leitor, fluxo)?)),
        None => None,
    };
    let gravador = abrir_gravador(saida).map_err(em("criar o arquivo comprimido"))?;
    let mut destino = HashMap::from([(video, somar_a_imagem(&gravador, &leitor, video, &original, ajustes.kbps)?)]);
    if let Some((fluxo, formato)) = som {
        destino.insert(fluxo, somar_o_som(&gravador, &leitor, fluxo, formato, ajustes.kbps_do_som)?);
    }
    unsafe { gravador.BeginWriting() }.map_err(em("começar a gravar"))?;
    copiar_amostras(&leitor, &gravador, destino, &mut Andamento::novo(video, duracao, avisar))?;
    unsafe { gravador.Finalize() }.map_err(em("fechar o arquivo comprimido"))
}

fn ler_o_original(nativo: &IMFMediaType, lado_menor: u32) -> Original {
    let tamanho = unsafe { nativo.GetUINT64(&MF_MT_FRAME_SIZE) }.unwrap_or(0);
    let taxa = unsafe { nativo.GetUINT64(&MF_MT_FRAME_RATE) }
        .ok()
        .filter(|taxa| *taxa as u32 != 0 && taxa >> 32 != 0)
        .unwrap_or(TAXA_QUANDO_FALTA);
    let (largura, altura) = tamanho_de_saida((tamanho >> 32) as u32, tamanho as u32, lado_menor);
    let rotacao = unsafe { nativo.GetUINT32(&MF_MT_VIDEO_ROTATION) }.ok().filter(|&graus| graus != 0);
    Original { imagem: Imagem { largura, altura, taxa }, rotacao }
}

fn preparar_a_imagem(leitor: &IMFSourceReader, video: u32, imagem: Imagem) -> Result<(), String> {
    let lido = tipo_nv12(imagem).map_err(em("criar um tipo de mídia"))?;
    unsafe {
        let _ = leitor.SetStreamSelection(TODOS_OS_FLUXOS, false);
        let _ = leitor.SetStreamSelection(video, true);
        leitor.SetCurrentMediaType(video, None, &lido)
    }
    .map_err(em("preparar a leitura em tamanho menor"))
}

fn preparar_o_som(leitor: &IMFSourceReader, fluxo: u32) -> Result<Som, String> {
    let formato = formato_do_som(&tipo_nativo(leitor, fluxo)?);
    let lido = tipo_pcm(formato).map_err(em("criar um tipo de mídia"))?;
    unsafe {
        let _ = leitor.SetStreamSelection(fluxo, true);
        leitor.SetCurrentMediaType(fluxo, None, &lido)
    }
    .map_err(em("preparar a leitura do som"))?;
    Ok(formato)
}

fn formato_do_som(nativo: &IMFMediaType) -> Som {
    let amostras = unsafe { nativo.GetUINT32(&MF_MT_AUDIO_SAMPLES_PER_SECOND) }.ok();
    let canais = unsafe { nativo.GetUINT32(&MF_MT_AUDIO_NUM_CHANNELS) }.ok();
    Som {
        amostras: if amostras == Some(44_100) { 44_100 } else { 48_000 },
        canais: if canais == Some(1) { 1 } else { 2 },
    }
}

fn somar_a_imagem(
    gravador: &IMFSinkWriter,
    leitor: &IMFSourceReader,
    video: u32,
    original: &Original,
    kbps: u32,
) -> Result<u32, String> {
    let saida = tipo_h264(original.imagem, kbps).map_err(em("criar um tipo de mídia"))?;
    if let Some(graus) = original.rotacao {
        unsafe { saida.SetUINT32(&MF_MT_VIDEO_ROTATION, graus) }.map_err(em("guardar a rotação"))?;
    }
    let lido = unsafe { leitor.GetCurrentMediaType(video) }.map_err(em("ler o formato de leitura do vídeo"))?;
    somar_fluxo(gravador, &saida, &lido).map_err(em("somar a imagem ao arquivo"))
}

fn somar_o_som(
    gravador: &IMFSinkWriter,
    leitor: &IMFSourceReader,
    fluxo: u32,
    formato: Som,
    kbps: u32,
) -> Result<u32, String> {
    let saida = tipo_aac(formato, kbps).map_err(em("criar um tipo de mídia"))?;
    let lido = unsafe { leitor.GetCurrentMediaType(fluxo) }.map_err(em("ler o formato de leitura do som"))?;
    somar_fluxo(gravador, &saida, &lido).map_err(em("somar o som ao arquivo"))
}
