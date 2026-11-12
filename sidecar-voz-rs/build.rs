use winresource::{VersionInfo, WindowsResource};

const VERSAO: &str = "1.0.0.0";
const VERSAO_FIXA: u64 = 1 << 48;
const INGLES_DOS_ESTADOS_UNIDOS: u16 = 0x0409;
const ICONE: &str = "../mobile-native/desktopApp/icons/astra.ico";

fn main() {
    println!("cargo:rerun-if-changed={ICONE}");
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("windows") {
        return;
    }
    let mut recurso = WindowsResource::new();
    recurso
        .set_icon(ICONE)
        .set_language(INGLES_DOS_ESTADOS_UNIDOS)
        .set_version_info(VersionInfo::FILEVERSION, VERSAO_FIXA)
        .set_version_info(VersionInfo::PRODUCTVERSION, VERSAO_FIXA)
        .set("CompanyName", "Astra")
        .set("FileDescription", "Astra")
        .set("FileVersion", VERSAO)
        .set("InternalName", "astra-voz")
        .set("LegalCopyright", "Astra")
        .set("OriginalFilename", "astra-voz.exe")
        .set("ProductName", "Astra")
        .set("ProductVersion", VERSAO);
    recurso.compile().expect("embutir ícone e versão no astra-voz.exe");
}
