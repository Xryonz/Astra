mod codificadores;
mod janelas;
mod monitores;
mod pixels;
mod placas;

pub use codificadores::codificadores_h264_da_placa;
pub use janelas::{Janela, listar_janelas};
pub use monitores::{Monitor, listar_monitores};
pub use placas::{Placa, nome_do_fabricante, placas_de_hardware};
