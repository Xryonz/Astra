use super::pixels::garantir_pixels_reais;
use std::time::Duration;
use windows::Win32::Foundation::E_POINTER;
use windows::Win32::Graphics::Direct3D11::{ID3D11Device, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::{
    DXGI_ERROR_ACCESS_LOST, DXGI_ERROR_NOT_FOUND, DXGI_ERROR_WAIT_TIMEOUT, DXGI_OUTDUPL_FRAME_INFO, IDXGIDevice,
    IDXGIOutput1, IDXGIOutputDuplication, IDXGIResource,
};
use windows::core::Interface;

pub struct CapturaDoMonitor {
    placa: ID3D11Device,
    saida: u32,
    duplicacao: Option<IDXGIOutputDuplication>,
    tamanho: (u32, u32),
}

pub struct QuadroDoMonitor {
    pub textura: ID3D11Texture2D,
    _soltura: Soltura,
}

struct Soltura(IDXGIOutputDuplication);

pub enum ColheitaDoMonitor {
    Quadro(QuadroDoMonitor),
    Nada,
    Perdido,
}

impl CapturaDoMonitor {
    pub fn abrir(placa: &ID3D11Device, saida: u32) -> windows::core::Result<Self> {
        let mut captura = Self { placa: placa.clone(), saida, duplicacao: None, tamanho: (0, 0) };
        captura.remontar()?;
        Ok(captura)
    }

    pub fn tamanho(&self) -> (u32, u32) {
        self.tamanho
    }

    pub fn perdida(&self) -> bool {
        self.duplicacao.is_none()
    }

    pub fn remontar(&mut self) -> windows::core::Result<()> {
        garantir_pixels_reais();
        self.duplicacao = None;
        let dxgi: IDXGIDevice = self.placa.cast()?;
        let saida: IDXGIOutput1 = unsafe { dxgi.GetAdapter()?.EnumOutputs(self.saida)? }.cast()?;
        let duplicacao = unsafe { saida.DuplicateOutput(&self.placa)? };
        let modo = unsafe { duplicacao.GetDesc() }.ModeDesc;
        self.tamanho = (modo.Width, modo.Height);
        self.duplicacao = Some(duplicacao);
        Ok(())
    }

    pub fn colher(&mut self, espera: Duration, so_se_mudou: bool) -> windows::core::Result<ColheitaDoMonitor> {
        let Some(duplicacao) = &self.duplicacao else { return Ok(ColheitaDoMonitor::Perdido) };
        let milissegundos = u32::try_from(espera.as_millis()).unwrap_or(u32::MAX);
        let mut info = DXGI_OUTDUPL_FRAME_INFO::default();
        let mut recurso: Option<IDXGIResource> = None;
        match unsafe { duplicacao.AcquireNextFrame(milissegundos, &mut info, &mut recurso) } {
            Ok(()) => {}
            Err(erro) if erro.code() == DXGI_ERROR_WAIT_TIMEOUT => return Ok(ColheitaDoMonitor::Nada),
            Err(erro) if erro.code() == DXGI_ERROR_ACCESS_LOST => {
                self.duplicacao = None;
                return Ok(ColheitaDoMonitor::Perdido);
            }
            Err(erro) => return Err(erro),
        }
        let soltura = Soltura(duplicacao.clone());
        if so_se_mudou && info.LastPresentTime == 0 {
            return Ok(ColheitaDoMonitor::Nada);
        }
        let textura: ID3D11Texture2D = recurso.ok_or(E_POINTER)?.cast()?;
        Ok(ColheitaDoMonitor::Quadro(QuadroDoMonitor { textura, _soltura: soltura }))
    }
}

pub fn o_monitor_saiu(erro: &windows::core::Error) -> bool {
    erro.code() == DXGI_ERROR_NOT_FOUND
}

impl Drop for Soltura {
    fn drop(&mut self) {
        let _ = unsafe { self.0.ReleaseFrame() };
    }
}
