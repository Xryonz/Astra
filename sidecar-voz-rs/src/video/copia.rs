use crate::tela::em;
use std::collections::HashMap;
use windows::Win32::Media::MediaFoundation::{
    IMFSample, IMFSinkWriter, IMFSourceReader, MF_SOURCE_READER_ALL_STREAMS, MF_SOURCE_READERF_ENDOFSTREAM,
    MF_SOURCE_READERF_ERROR, MF_SOURCE_READERF_STREAMTICK,
};

const TODOS_OS_FLUXOS: u32 = MF_SOURCE_READER_ALL_STREAMS.0 as u32;
const COM_ERRO: u32 = MF_SOURCE_READERF_ERROR.0 as u32;
const NO_FIM: u32 = MF_SOURCE_READERF_ENDOFSTREAM.0 as u32;
const LACUNA: u32 = MF_SOURCE_READERF_STREAMTICK.0 as u32;
const PASSO_MINIMO: f64 = 0.01;

pub struct Andamento<F> {
    video: u32,
    duracao: u64,
    ultimo: f64,
    avisar: F,
}

impl<F: FnMut(f64)> Andamento<F> {
    pub fn novo(video: u32, duracao: u64, avisar: F) -> Self {
        Self { video, duracao, ultimo: -1.0, avisar }
    }

    fn registrar(&mut self, fluxo: u32, instante: i64) {
        if fluxo != self.video || self.duracao == 0 {
            return;
        }
        let fracao = (instante as f64 / self.duracao as f64).min(1.0);
        if fracao - self.ultimo >= PASSO_MINIMO {
            self.ultimo = fracao;
            (self.avisar)(fracao);
        }
    }
}

struct Lida {
    fluxo: u32,
    sinais: u32,
    instante: i64,
    amostra: Option<IMFSample>,
}

pub fn copiar_amostras<F: FnMut(f64)>(
    leitor: &IMFSourceReader,
    gravador: &IMFSinkWriter,
    mut destino: HashMap<u32, u32>,
    andamento: &mut Andamento<F>,
) -> Result<(), String> {
    while !destino.is_empty() {
        let lida = ler_amostra(leitor)?;
        let fluxo = destino.get(&lida.fluxo).copied();
        if let (Some(amostra), Some(fluxo)) = (&lida.amostra, fluxo) {
            unsafe { gravador.WriteSample(fluxo, amostra) }.map_err(em("gravar o vídeo comprimido"))?;
        }
        if lida.sinais & COM_ERRO != 0 {
            return Err(format!("o leitor parou com erro no fluxo {}", lida.fluxo));
        }
        if let Some(fluxo) = fluxo.filter(|_| lida.sinais & LACUNA != 0) {
            let _ = unsafe { gravador.SendStreamTick(fluxo, lida.instante) };
        }
        if lida.sinais & NO_FIM != 0 {
            destino.remove(&lida.fluxo);
        }
        andamento.registrar(lida.fluxo, lida.instante);
    }
    Ok(())
}

fn ler_amostra(leitor: &IMFSourceReader) -> Result<Lida, String> {
    let mut lida = Lida { fluxo: 0, sinais: 0, instante: 0, amostra: None };
    unsafe {
        leitor.ReadSample(
            TODOS_OS_FLUXOS,
            0,
            Some(&mut lida.fluxo),
            Some(&mut lida.sinais),
            Some(&mut lida.instante),
            Some(&mut lida.amostra),
        )
    }
    .map_err(em("ler o vídeo"))?;
    Ok(lida)
}
