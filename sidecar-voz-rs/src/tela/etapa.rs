pub fn em(etapa: &'static str) -> impl Fn(windows::core::Error) -> String {
    move |erro| format!("{etapa}: {erro}")
}
