use windows::Win32::Media::{timeBeginPeriod, timeEndPeriod};

#[derive(Clone, Copy)]
pub enum Relogio {
    Padrao,
    Fino,
}

impl Relogio {
    pub fn nome(self) -> &'static str {
        match self {
            Self::Padrao => "padrao",
            Self::Fino => "1ms",
        }
    }

    pub fn valer(self) -> Option<RelogioFino> {
        matches!(self, Self::Fino).then(RelogioFino::pedir)
    }
}

pub struct RelogioFino;

impl RelogioFino {
    fn pedir() -> Self {
        unsafe { timeBeginPeriod(1) };
        Self
    }
}

impl Drop for RelogioFino {
    fn drop(&mut self) {
        unsafe { timeEndPeriod(1) };
    }
}
