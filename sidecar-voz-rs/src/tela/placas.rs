use windows::Win32::Foundation::LUID;
use windows::Win32::Graphics::Dxgi::{CreateDXGIFactory1, DXGI_ADAPTER_FLAG_SOFTWARE, IDXGIAdapter1, IDXGIFactory1};

pub struct Placa {
    pub nome: String,
    pub fabricante: u32,
    pub luid: LUID,
    pub adaptador: IDXGIAdapter1,
}

pub fn placas_de_hardware() -> windows::core::Result<Vec<Placa>> {
    let fabrica: IDXGIFactory1 = unsafe { CreateDXGIFactory1()? };
    let mut placas = Vec::new();
    let mut numero = 0;
    while let Ok(adaptador) = unsafe { fabrica.EnumAdapters1(numero) } {
        numero += 1;
        let (placa, de_software) = descrever(adaptador)?;
        if !de_software {
            placas.push(placa);
        }
    }
    Ok(placas)
}

pub fn placa_padrao() -> windows::core::Result<Placa> {
    let fabrica: IDXGIFactory1 = unsafe { CreateDXGIFactory1()? };
    placa_do_adaptador(unsafe { fabrica.EnumAdapters1(0)? })
}

pub fn placa_do_adaptador(adaptador: IDXGIAdapter1) -> windows::core::Result<Placa> {
    descrever(adaptador).map(|(placa, _)| placa)
}

fn descrever(adaptador: IDXGIAdapter1) -> windows::core::Result<(Placa, bool)> {
    let desc = unsafe { adaptador.GetDesc1()? };
    let fim = desc.Description.iter().position(|&c| c == 0).unwrap_or(desc.Description.len());
    let placa = Placa {
        nome: String::from_utf16_lossy(&desc.Description[..fim]),
        fabricante: desc.VendorId,
        luid: desc.AdapterLuid,
        adaptador,
    };
    Ok((placa, desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE.0 as u32 != 0))
}

pub fn nome_do_fabricante(codigo: u32) -> &'static str {
    match codigo {
        0x8086 => "Intel",
        0x10DE => "NVIDIA",
        0x1002 | 0x1022 => "AMD",
        _ => "outro",
    }
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn fabricante_sai_do_codigo_do_windows() {
        assert_eq!(nome_do_fabricante(0x8086), "Intel");
        assert_eq!(nome_do_fabricante(0x10DE), "NVIDIA");
        assert_eq!(nome_do_fabricante(0x1002), "AMD");
        assert_eq!(nome_do_fabricante(0x1414), "outro");
    }
}
