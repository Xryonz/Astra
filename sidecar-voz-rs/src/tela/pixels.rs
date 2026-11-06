use std::sync::Once;
use windows::Win32::UI::HiDpi::{DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2, SetProcessDpiAwarenessContext};
use windows::Win32::UI::WindowsAndMessaging::SetProcessDPIAware;

static PIXELS_DE_VERDADE: Once = Once::new();

pub fn garantir_pixels_reais() {
    PIXELS_DE_VERDADE.call_once(|| {
        if unsafe { SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2) }.is_err() {
            let _ = unsafe { SetProcessDPIAware() };
        }
    });
}
