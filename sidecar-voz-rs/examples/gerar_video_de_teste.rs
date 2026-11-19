#[path = "../src/video/gravador.rs"]
mod gravador;
#[path = "../src/video/tipos.rs"]
mod tipos;

use gravador::{abrir_gravador, somar_fluxo};
use tipos::{Imagem, Som, par, tipo_aac, tipo_h264, tipo_nv12, tipo_pcm};
use windows::Win32::Media::MediaFoundation::{
    IMFSample, IMFSinkWriter, MF_MT_VIDEO_ROTATION, MF_VERSION, MFCreateMemoryBuffer, MFCreateSample,
    MFSTARTUP_NOSOCKET, MFShutdown, MFStartup,
};
use windows::Win32::System::Com::{COINIT_MULTITHREADED, CoInitializeEx};
use windows::core::Result;

const UM_SEGUNDO: i64 = 10_000_000;
const KBPS_DO_SOM: u32 = 128;
const LADO_DO_BIT: usize = 16;
const BITS_DO_CONTADOR: usize = 16;
const LADO_DO_RUIDO: usize = 8;
const TOM_HZ: f64 = 440.0;
const ESTALO_HZ: f64 = 2000.0;
const DURACAO_DO_ESTALO_MS: u64 = 10;
const USO: &str = "uso: gerar_video_de_teste <saida.mp4> [largura] [altura] [fps] [segundos] \
                   [--sem-som] [--sem-imagem] [--mono44] [--rotacao=90] [--vfr] [--ruido] [--kbps=8000]";

struct Pedido {
    saida: String,
    largura: u32,
    altura: u32,
    fps: u32,
    segundos: u32,
    som: Option<Som>,
    com_imagem: bool,
    rotacao: u32,
    taxa_variavel: bool,
    ruido: bool,
    kbps: u32,
}

struct Resumo {
    quadros: u32,
    duracao: i64,
}

fn main() {
    let Some(pedido) = ler_o_pedido() else {
        eprintln!("{USO}");
        std::process::exit(2);
    };
    unsafe {
        let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
        MFStartup(MF_VERSION, MFSTARTUP_NOSOCKET).expect("iniciar o Media Foundation");
    }
    let resultado = gravar(&pedido);
    let _ = unsafe { MFShutdown() };
    match resultado {
        Ok(resumo) => println!("{}", relatar(&pedido, &resumo)),
        Err(erro) => {
            eprintln!("gerar {}: {erro}", pedido.saida);
            std::process::exit(1);
        }
    }
}

fn ler_o_pedido() -> Option<Pedido> {
    let argumentos: Vec<String> = std::env::args().skip(1).collect();
    let (opcoes, posicionais): (Vec<&String>, Vec<&String>) = argumentos.iter().partition(|a| a.starts_with("--"));
    let numero = |indice: usize, padrao: u32| posicionais.get(indice).and_then(|t| t.parse().ok()).unwrap_or(padrao);
    let valor = |nome: &str, padrao: u32| {
        opcoes.iter().find_map(|o| o.strip_prefix(nome)?.strip_prefix('=')?.parse().ok()).unwrap_or(padrao)
    };
    let pediu = |nome: &str| opcoes.iter().any(|o| o.as_str() == nome);
    let som = match (pediu("--sem-som"), pediu("--mono44")) {
        (true, _) => None,
        (false, true) => Some(Som { amostras: 44_100, canais: 1 }),
        (false, false) => Some(Som { amostras: 48_000, canais: 2 }),
    };
    Some(Pedido {
        saida: posicionais.first()?.to_string(),
        largura: numero(1, 1920),
        altura: numero(2, 1080),
        fps: numero(3, 30).max(1),
        segundos: numero(4, 5).max(1),
        som,
        com_imagem: !pediu("--sem-imagem"),
        rotacao: valor("--rotacao", 0),
        taxa_variavel: pediu("--vfr"),
        ruido: pediu("--ruido"),
        kbps: valor("--kbps", 8000),
    })
}

fn gravar(pedido: &Pedido) -> Result<Resumo> {
    let gravador = abrir_gravador(&pedido.saida)?;
    let fluxo_da_imagem = if pedido.com_imagem { Some(somar_a_imagem(&gravador, pedido)?) } else { None };
    let fluxo_do_som = match pedido.som {
        Some(som) => Some((somar_fluxo(&gravador, &tipo_aac(som, KBPS_DO_SOM)?, &tipo_pcm(som)?)?, som)),
        None => None,
    };
    unsafe { gravador.BeginWriting()? };
    let mut quadro = vec![0u8; (pedido.largura * pedido.altura * 3 / 2) as usize];
    let fim = i64::from(pedido.segundos) * UM_SEGUNDO;
    let (mut instante, mut numero, mut som_escrito) = (0i64, 0u32, 0u64);
    while instante < fim {
        let duracao = duracao_do_quadro(pedido, numero);
        if let Some(fluxo) = fluxo_da_imagem {
            pintar(&mut quadro, pedido, numero);
            escrever(&gravador, fluxo, &quadro, instante, duracao)?;
        }
        if let Some((fluxo, som)) = fluxo_do_som {
            som_escrito = escrever_som_ate(&gravador, fluxo, som, som_escrito, instante + duracao)?;
        }
        instante += duracao;
        numero += 1;
    }
    unsafe { gravador.Finalize()? };
    Ok(Resumo { quadros: numero, duracao: instante })
}

fn somar_a_imagem(gravador: &IMFSinkWriter, pedido: &Pedido) -> Result<u32> {
    let imagem = Imagem { largura: pedido.largura, altura: pedido.altura, taxa: par(pedido.fps, 1) };
    let saida = tipo_h264(imagem, pedido.kbps)?;
    if pedido.rotacao != 0 {
        unsafe { saida.SetUINT32(&MF_MT_VIDEO_ROTATION, pedido.rotacao)? };
    }
    somar_fluxo(gravador, &saida, &tipo_nv12(imagem)?)
}

fn duracao_do_quadro(pedido: &Pedido, numero: u32) -> i64 {
    match (pedido.taxa_variavel, numero % 3) {
        (true, 2) => UM_SEGUNDO / 15,
        (true, _) => UM_SEGUNDO / 60,
        (false, _) => UM_SEGUNDO / i64::from(pedido.fps),
    }
}

fn pintar(quadro: &mut [u8], pedido: &Pedido, numero: u32) {
    let (largura, altura) = (pedido.largura as usize, pedido.altura as usize);
    let (luz, cor) = quadro.split_at_mut(largura * altura);
    if pedido.ruido {
        pintar_ruido(luz, largura, numero);
    } else {
        pintar_degrade(luz, largura, numero);
    }
    for (indice, byte) in cor.iter_mut().enumerate() {
        *byte = 128 + ((indice / 2 + numero as usize) % 64) as u8;
    }
    marcar_o_numero(luz, largura, altura, numero);
}

fn pintar_degrade(luz: &mut [u8], largura: usize, numero: u32) {
    for (y, linha) in luz.chunks_exact_mut(largura).enumerate() {
        for (x, byte) in linha.iter_mut().enumerate() {
            *byte = ((x + y + numero as usize * 8) % 256) as u8;
        }
    }
}

fn pintar_ruido(luz: &mut [u8], largura: usize, numero: u32) {
    let mut semente = numero.wrapping_mul(0x9E37_79B9) | 1;
    let mut fileira = vec![0u8; largura.div_ceil(LADO_DO_RUIDO)];
    for (y, linha) in luz.chunks_exact_mut(largura).enumerate() {
        if y % LADO_DO_RUIDO == 0 {
            fileira.iter_mut().for_each(|valor| *valor = sortear(&mut semente) as u8);
        }
        for (pedaco, &valor) in linha.chunks_mut(LADO_DO_RUIDO).zip(&fileira) {
            pedaco.fill(valor);
        }
    }
}

fn marcar_o_numero(luz: &mut [u8], largura: usize, altura: usize, numero: u32) {
    if altura < LADO_DO_BIT {
        return;
    }
    for bit in 0..BITS_DO_CONTADOR.min(largura / LADO_DO_BIT) {
        let valor = if (numero >> bit) & 1 == 1 { 235 } else { 16 };
        let comeco = bit * LADO_DO_BIT;
        for linha in luz.chunks_exact_mut(largura).take(LADO_DO_BIT) {
            linha[comeco..comeco + LADO_DO_BIT].fill(valor);
        }
    }
}

fn sortear(semente: &mut u32) -> u32 {
    *semente ^= *semente << 13;
    *semente ^= *semente >> 17;
    *semente ^= *semente << 5;
    *semente
}

fn escrever_som_ate(gravador: &IMFSinkWriter, fluxo: u32, som: Som, escrito: u64, ate: i64) -> Result<u64> {
    let por_segundo = u64::from(som.amostras);
    let alvo = ate as u64 * por_segundo / UM_SEGUNDO as u64;
    if alvo <= escrito {
        return Ok(escrito);
    }
    let instante = (escrito * UM_SEGUNDO as u64 / por_segundo) as i64;
    let duracao = ((alvo - escrito) * UM_SEGUNDO as u64 / por_segundo) as i64;
    escrever(gravador, fluxo, &gerar_som(som, escrito, alvo), instante, duracao)?;
    Ok(alvo)
}

fn gerar_som(som: Som, de: u64, ate: u64) -> Vec<u8> {
    let mut dados = Vec::with_capacity(((ate - de) * u64::from(som.canais) * 2) as usize);
    for indice in de..ate {
        let valor = amostra_de_som(indice, som.amostras).to_le_bytes();
        for _ in 0..som.canais {
            dados.extend_from_slice(&valor);
        }
    }
    dados
}

fn amostra_de_som(indice: u64, amostras: u32) -> i16 {
    let tempo = indice as f64 / f64::from(amostras);
    let no_estalo = indice % u64::from(amostras) < u64::from(amostras) * DURACAO_DO_ESTALO_MS / 1000;
    let (frequencia, volume) = if no_estalo { (ESTALO_HZ, 0.9) } else { (TOM_HZ, 0.25) };
    ((tempo * frequencia * std::f64::consts::TAU).sin() * volume * f64::from(i16::MAX)) as i16
}

fn escrever(gravador: &IMFSinkWriter, fluxo: u32, dados: &[u8], instante: i64, duracao: i64) -> Result<()> {
    let amostra = amostra_com(dados, instante, duracao)?;
    unsafe { gravador.WriteSample(fluxo, &amostra) }
}

fn amostra_com(dados: &[u8], instante: i64, duracao: i64) -> Result<IMFSample> {
    unsafe {
        let buffer = MFCreateMemoryBuffer(dados.len() as u32)?;
        let mut destino = std::ptr::null_mut();
        buffer.Lock(&mut destino, None, None)?;
        std::ptr::copy_nonoverlapping(dados.as_ptr(), destino, dados.len());
        buffer.Unlock()?;
        buffer.SetCurrentLength(dados.len() as u32)?;
        let amostra = MFCreateSample()?;
        amostra.AddBuffer(&buffer)?;
        amostra.SetSampleTime(instante)?;
        amostra.SetSampleDuration(duracao)?;
        Ok(amostra)
    }
}

fn relatar(pedido: &Pedido, resumo: &Resumo) -> serde_json::Value {
    serde_json::json!({
        "arquivo": pedido.saida,
        "quadros": resumo.quadros,
        "duracaoMs": resumo.duracao / 10_000,
        "largura": pedido.largura,
        "altura": pedido.altura,
        "imagem": pedido.com_imagem,
        "som": pedido.som.map(|som| format!("{} Hz, {} canais", som.amostras, som.canais)),
        "rotacao": pedido.rotacao,
        "vfr": pedido.taxa_variavel,
        "ruido": pedido.ruido,
    })
}
