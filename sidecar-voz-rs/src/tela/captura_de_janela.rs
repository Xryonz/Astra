use std::time::Duration;
use windows::Foundation::TypedEventHandler;
use windows::Graphics::Capture::{
    Direct3D11CaptureFrame, Direct3D11CaptureFramePool, GraphicsCaptureItem, GraphicsCaptureSession,
};
use windows::Graphics::DirectX::Direct3D11::IDirect3DDevice;
use windows::Graphics::DirectX::DirectXPixelFormat;
use windows::Graphics::SizeInt32;
use windows::Win32::Foundation::{CloseHandle, HANDLE, HWND, WAIT_OBJECT_0};
use windows::Win32::Graphics::Direct3D11::{ID3D11Device, ID3D11Texture2D};
use windows::Win32::Graphics::Dxgi::IDXGIDevice;
use windows::Win32::System::Threading::{CreateEventW, SetEvent, WaitForSingleObject};
use windows::Win32::System::WinRT::Direct3D11::{CreateDirect3D11DeviceFromDXGIDevice, IDirect3DDxgiInterfaceAccess};
use windows::Win32::System::WinRT::Graphics::Capture::IGraphicsCaptureItemInterop;
use windows::core::{IInspectable, Interface, factory};

const QUADROS_NA_PISCINA: i32 = 2;
const FORMATO: DirectXPixelFormat = DirectXPixelFormat::B8G8R8A8UIntNormalized;

pub struct CapturaDeJanela {
    sessao: GraphicsCaptureSession,
    piscina: Direct3D11CaptureFramePool,
    dispositivo: IDirect3DDevice,
    tamanho: SizeInt32,
    chegou: HANDLE,
    aviso: i64,
    _item: GraphicsCaptureItem,
}

pub struct QuadroDaJanela {
    quadro: Direct3D11CaptureFrame,
    pub textura: ID3D11Texture2D,
}

pub enum Colheita {
    Quadro(QuadroDaJanela),
    Nada,
    MudouDeTamanho(u32, u32),
}

impl CapturaDeJanela {
    pub fn abrir(placa: &ID3D11Device, janela: HWND) -> windows::core::Result<Self> {
        let interop = factory::<GraphicsCaptureItem, IGraphicsCaptureItemInterop>()?;
        let item: GraphicsCaptureItem = unsafe { interop.CreateForWindow(janela)? };
        let dxgi: IDXGIDevice = placa.cast()?;
        let dispositivo: IDirect3DDevice = unsafe { CreateDirect3D11DeviceFromDXGIDevice(&dxgi)? }.cast()?;
        let tamanho = item.Size()?;
        let piscina = Direct3D11CaptureFramePool::CreateFreeThreaded(&dispositivo, FORMATO, QUADROS_NA_PISCINA, tamanho)?;
        let chegou = unsafe { CreateEventW(None, false, false, None)? };
        let aviso = avisar_a_cada_quadro(&piscina, chegou)?;
        let sessao = piscina.CreateCaptureSession(&item)?;
        let _ = sessao.SetIsBorderRequired(false);
        sessao.StartCapture()?;
        Ok(Self { sessao, piscina, dispositivo, tamanho, chegou, aviso, _item: item })
    }

    pub fn tamanho(&self) -> (u32, u32) {
        (self.tamanho.Width.max(0) as u32, self.tamanho.Height.max(0) as u32)
    }

    pub fn esperar(&self, limite: Duration) -> bool {
        let milissegundos = u32::try_from(limite.as_millis()).unwrap_or(u32::MAX);
        let resposta = unsafe { WaitForSingleObject(self.chegou, milissegundos) };
        resposta == WAIT_OBJECT_0
    }

    pub fn colher(&mut self) -> windows::core::Result<Colheita> {
        let Some(quadro) = self.mais_novo()? else { return Ok(Colheita::Nada) };
        let conteudo = quadro.ContentSize()?;
        if conteudo != self.tamanho {
            let _ = quadro.Close();
            self.piscina.Recreate(&self.dispositivo, FORMATO, QUADROS_NA_PISCINA, conteudo)?;
            self.tamanho = conteudo;
            let (largura, altura) = self.tamanho();
            return Ok(Colheita::MudouDeTamanho(largura, altura));
        }
        let acesso: IDirect3DDxgiInterfaceAccess = quadro.Surface()?.cast()?;
        let textura = unsafe { acesso.GetInterface()? };
        Ok(Colheita::Quadro(QuadroDaJanela { quadro, textura }))
    }

    fn mais_novo(&self) -> windows::core::Result<Option<Direct3D11CaptureFrame>> {
        let mut mais_novo: Option<Direct3D11CaptureFrame> = None;
        while let Some(quadro) = self.proximo_da_piscina()? {
            if let Some(antigo) = mais_novo.replace(quadro) {
                let _ = antigo.Close();
            }
        }
        Ok(mais_novo)
    }

    fn proximo_da_piscina(&self) -> windows::core::Result<Option<Direct3D11CaptureFrame>> {
        let mut quadro = std::ptr::null_mut();
        unsafe { (self.piscina.vtable().TryGetNextFrame)(self.piscina.as_raw(), &mut quadro).ok()? };
        Ok((!quadro.is_null()).then(|| unsafe { Direct3D11CaptureFrame::from_raw(quadro) }))
    }
}

fn avisar_a_cada_quadro(piscina: &Direct3D11CaptureFramePool, chegou: HANDLE) -> windows::core::Result<i64> {
    let evento = chegou.0 as usize;
    piscina.FrameArrived(&TypedEventHandler::<Direct3D11CaptureFramePool, IInspectable>::new(move |_, _| {
        unsafe { SetEvent(HANDLE(evento as *mut _)) }
    }))
}

impl Drop for CapturaDeJanela {
    fn drop(&mut self) {
        let _ = self.piscina.RemoveFrameArrived(self.aviso);
        let _ = self.sessao.Close();
        let _ = self.piscina.Close();
        let _ = unsafe { CloseHandle(self.chegou) };
    }
}

impl Drop for QuadroDaJanela {
    fn drop(&mut self) {
        let _ = self.quadro.Close();
    }
}
