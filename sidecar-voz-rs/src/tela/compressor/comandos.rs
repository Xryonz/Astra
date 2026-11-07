use serde::Serialize;
use windows::Win32::Media::MediaFoundation::{
    CODECAPI_AVEncCommonMaxBitRate, CODECAPI_AVEncCommonMeanBitRate, CODECAPI_AVEncCommonRateControlMode,
    CODECAPI_AVEncH264CABACEnable, CODECAPI_AVEncMPVDefaultBPictureCount, CODECAPI_AVEncVideoForceKeyFrame,
    CODECAPI_AVLowLatencyMode, ICodecAPI, eAVEncCommonRateControlMode_PeakConstrainedVBR,
};
use windows::Win32::System::Variant::{VARIANT, VT_BOOL, VT_UI4};
use windows::core::GUID;

#[derive(Clone, Copy, Serialize)]
pub struct Aceitos {
    pub baixa_latencia: bool,
    pub taxa_variavel: bool,
    pub cabac: bool,
    pub sem_quadros_b: bool,
}

pub struct Comandos(pub ICodecAPI);

impl Comandos {
    pub fn configurar(&self, kbps: u32) -> Aceitos {
        let modo_de_pico = numero(eAVEncCommonRateControlMode_PeakConstrainedVBR.0 as u32);
        Aceitos {
            baixa_latencia: self.definir(&CODECAPI_AVLowLatencyMode, logico(true)),
            taxa_variavel: self.definir(&CODECAPI_AVEncCommonRateControlMode, modo_de_pico) && self.mudar_taxa(kbps),
            cabac: self.definir(&CODECAPI_AVEncH264CABACEnable, logico(true)),
            sem_quadros_b: self.definir(&CODECAPI_AVEncMPVDefaultBPictureCount, numero(0)),
        }
    }

    pub fn mudar_taxa(&self, kbps: u32) -> bool {
        self.definir(&CODECAPI_AVEncCommonMeanBitRate, numero(kbps * 1000))
            && self.definir(&CODECAPI_AVEncCommonMaxBitRate, numero(kbps * 2000))
    }

    pub fn pedir_quadro_chave(&self) -> bool {
        self.definir(&CODECAPI_AVEncVideoForceKeyFrame, numero(1))
    }

    fn definir(&self, chave: &GUID, valor: VARIANT) -> bool {
        unsafe { self.0.SetValue(chave, &valor) }.is_ok()
    }
}

fn numero(valor: u32) -> VARIANT {
    let mut variante = VARIANT::default();
    unsafe {
        let dentro = &mut variante.Anonymous.Anonymous;
        dentro.vt = VT_UI4;
        dentro.Anonymous.ulVal = valor;
    }
    variante
}

fn logico(valor: bool) -> VARIANT {
    let mut variante = VARIANT::default();
    unsafe {
        let dentro = &mut variante.Anonymous.Anonymous;
        dentro.vt = VT_BOOL;
        dentro.Anonymous.boolVal = valor.into();
    }
    variante
}
