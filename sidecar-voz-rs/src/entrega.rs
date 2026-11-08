use std::io::{self, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::mpsc::{self, Receiver, SyncSender, TrySendError};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::time::Duration;

const MARCA_DO_QUADRO: u32 = 0x5654_5341;
const TAMANHO_DO_CABECALHO: usize = 24;
const QUADROS_NA_FILA: usize = 2;
const SOBRAS_GUARDADAS: usize = QUADROS_NA_FILA + 2;
const PRAZO_PARA_LIGAR: Duration = Duration::from_secs(3);
const PRAZO_PARA_ESCREVER: Duration = Duration::from_secs(2);
const PAUSA_DEPOIS_DE_FALHAR: Duration = Duration::from_secs(2);

pub struct Quadro {
    pub dados: Vec<u8>,
    pub largura: u32,
    pub altura: u32,
    pub passo: u32,
}

#[derive(Clone)]
pub struct Entrega {
    fila: SyncSender<(String, Quadro)>,
    sobras: Sobras,
}

#[derive(Clone, Default)]
struct Sobras(Arc<Mutex<Vec<Vec<u8>>>>);

impl Sobras {
    fn tirar(&self, tamanho: usize) -> Vec<u8> {
        let mut dados = self.trancar().pop().unwrap_or_default();
        dados.resize(tamanho, 0);
        dados
    }

    fn devolver(&self, dados: Vec<u8>) {
        let mut guardadas = self.trancar();
        if guardadas.len() < SOBRAS_GUARDADAS {
            guardadas.push(dados);
        }
    }

    fn trancar(&self) -> MutexGuard<'_, Vec<Vec<u8>>> {
        self.0.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

impl Entrega {
    pub fn do_ambiente() -> Option<Self> {
        let endereco = std::env::var("ASTRA_QUADROS").ok().filter(|endereco| !endereco.is_empty())?;
        let segredo = std::env::var("ASTRA_QUADROS_SEGREDO").unwrap_or_default();
        Self::para(endereco, segredo)
            .inspect_err(|erro| eprintln!("sem cano de quadros: {erro}"))
            .ok()
    }

    fn para(endereco: String, segredo: String) -> io::Result<Self> {
        let (fila, saida) = mpsc::sync_channel(QUADROS_NA_FILA);
        let sobras = Sobras::default();
        let devolvidas = sobras.clone();
        std::thread::Builder::new()
            .name("astra-quadros".into())
            .spawn(move || servir(&endereco, &segredo, &saida, &devolvidas))?;
        Ok(Self { fila, sobras })
    }

    pub fn reservar(&self, tamanho: usize) -> Vec<u8> {
        self.sobras.tirar(tamanho)
    }

    pub fn mandar(&self, par: &str, quadro: Quadro) {
        if quadro.dados.is_empty() {
            return;
        }
        match self.fila.try_send((par.to_owned(), quadro)) {
            Ok(()) => {}
            Err(TrySendError::Full((_, quadro))) => self.sobras.devolver(quadro.dados),
            Err(TrySendError::Disconnected(_)) => eprintln!("cano de quadros fechado"),
        }
    }
}

fn servir(endereco: &str, segredo: &str, fila: &Receiver<(String, Quadro)>, sobras: &Sobras) {
    let mut conexao: Option<TcpStream> = None;
    for (par, quadro) in fila {
        conexao = levar(conexao.take(), endereco, segredo, &par, &quadro);
        sobras.devolver(quadro.dados);
    }
}

fn levar(conexao: Option<TcpStream>, endereco: &str, segredo: &str, par: &str, quadro: &Quadro) -> Option<TcpStream> {
    let ligada = match conexao {
        Some(ligada) => ligada,
        None => match ligar(endereco, segredo) {
            Ok(ligada) => ligada,
            Err(erro) => {
                eprintln!("cano de quadros indisponível: {erro}");
                std::thread::sleep(PAUSA_DEPOIS_DE_FALHAR);
                return None;
            }
        },
    };
    match escrever(&ligada, par, quadro) {
        Ok(()) => Some(ligada),
        Err(erro) => {
            eprintln!("cano de quadros caiu: {erro}");
            None
        }
    }
}

fn ligar(endereco: &str, segredo: &str) -> io::Result<TcpStream> {
    let destino = endereco
        .to_socket_addrs()?
        .next()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "endereço sem destino"))?;
    let mut conexao = TcpStream::connect_timeout(&destino, PRAZO_PARA_LIGAR)?;
    conexao.set_write_timeout(Some(PRAZO_PARA_ESCREVER))?;
    conexao.set_nodelay(true)?;
    conexao.write_all(format!("{segredo}\n").as_bytes())?;
    Ok(conexao)
}

fn escrever(mut conexao: &TcpStream, par: &str, quadro: &Quadro) -> io::Result<()> {
    conexao.write_all(&cabecalho(par, quadro))?;
    conexao.write_all(par.as_bytes())?;
    conexao.write_all(&quadro.dados)
}

fn cabecalho(par: &str, quadro: &Quadro) -> [u8; TAMANHO_DO_CABECALHO] {
    let campos = [
        MARCA_DO_QUADRO,
        par.len() as u32,
        quadro.largura,
        quadro.altura,
        quadro.passo,
        quadro.dados.len() as u32,
    ];
    let mut bytes = [0u8; TAMANHO_DO_CABECALHO];
    for (pedaco, campo) in bytes.as_chunks_mut::<4>().0.iter_mut().zip(campos) {
        *pedaco = campo.to_le_bytes();
    }
    bytes
}

#[cfg(test)]
mod testes {
    use super::*;
    use std::io::{BufRead, BufReader, Read};
    use std::net::TcpListener;

    fn quadro_de(largura: u32, altura: u32) -> Quadro {
        let dados = (0..largura * altura * 3 / 2).map(|i| i as u8).collect();
        Quadro { dados, largura, altura, passo: largura }
    }

    #[test]
    fn o_cabecalho_tem_os_24_bytes_do_go() {
        let esperado: Vec<u8> = [0x5654_5341u32, 0, 320, 180, 320, 86400].iter().flat_map(|campo| campo.to_le_bytes()).collect();
        assert_eq!(cabecalho("", &quadro_de(320, 180)).to_vec(), esperado);
        assert_eq!(&cabecalho("ana", &quadro_de(320, 180))[4..8], &3u32.to_le_bytes());
    }

    #[test]
    fn o_quadro_chega_depois_do_segredo_com_cabecalho_par_e_dados() {
        let ouvinte = TcpListener::bind("127.0.0.1:0").unwrap();
        let endereco = ouvinte.local_addr().unwrap().to_string();
        let entrega = Entrega::para(endereco, "abc123".into()).unwrap();
        let quadro = quadro_de(4, 2);
        let dados = quadro.dados.clone();
        entrega.mandar("ana", quadro);

        let (conexao, _) = ouvinte.accept().unwrap();
        let mut leitor = BufReader::new(conexao);
        let mut segredo = String::new();
        leitor.read_line(&mut segredo).unwrap();
        assert_eq!(segredo, "abc123\n");
        let mut cabecalho_lido = [0u8; TAMANHO_DO_CABECALHO];
        leitor.read_exact(&mut cabecalho_lido).unwrap();
        let campo = |i: usize| u32::from_le_bytes(cabecalho_lido[i * 4..i * 4 + 4].try_into().unwrap());
        assert_eq!((campo(0), campo(1), campo(2), campo(3), campo(4), campo(5)), (MARCA_DO_QUADRO, 3, 4, 2, 4, 12));
        let mut par = [0u8; 3];
        leitor.read_exact(&mut par).unwrap();
        assert_eq!(&par, b"ana");
        let mut recebido = vec![0u8; dados.len()];
        leitor.read_exact(&mut recebido).unwrap();
        assert_eq!(recebido, dados);
    }

    #[test]
    fn quadro_vazio_nao_entra_na_fila() {
        let (fila, saida) = mpsc::sync_channel(QUADROS_NA_FILA);
        Entrega { fila, sobras: Sobras::default() }.mandar("", Quadro { dados: Vec::new(), largura: 1, altura: 1, passo: 1 });
        assert!(saida.try_recv().is_err());
    }

    #[test]
    fn quadro_descartado_com_a_fila_cheia_devolve_o_buffer_para_a_reserva() {
        let (fila, _saida) = mpsc::sync_channel(QUADROS_NA_FILA);
        let entrega = Entrega { fila, sobras: Sobras::default() };
        for _ in 0..=QUADROS_NA_FILA {
            entrega.mandar("ana", quadro_de(4, 2));
        }
        let reaproveitado = entrega.reservar(6);
        assert_eq!(reaproveitado.len(), 6);
        assert!(reaproveitado.capacity() >= 12, "veio o buffer do quadro descartado, não um novo");
    }

    #[test]
    fn a_reserva_guarda_poucos_buffers() {
        let sobras = Sobras::default();
        for _ in 0..SOBRAS_GUARDADAS + 3 {
            sobras.devolver(vec![0; 8]);
        }
        assert_eq!(sobras.trancar().len(), SOBRAS_GUARDADAS);
    }
}
