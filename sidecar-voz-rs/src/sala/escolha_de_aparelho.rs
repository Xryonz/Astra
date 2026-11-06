use crate::aparelhos::{self, Sentido};
use livekit::prelude::{AudioResult, PlatformAudio, PlayoutDeviceId, RecordingDeviceId};

#[derive(Default)]
pub struct Escolhas {
    entrada: String,
    saida: String,
}

impl Escolhas {
    pub fn de(&self, sentido: Sentido) -> &str {
        match sentido {
            Sentido::Entrada => &self.entrada,
            Sentido::Saida => &self.saida,
        }
    }

    pub fn definir(&mut self, sentido: Sentido, id: String) {
        match sentido {
            Sentido::Entrada => self.entrada = id,
            Sentido::Saida => self.saida = id,
        }
    }
}

pub async fn levar_ao_aparelho_certo(audio: &PlatformAudio, em_uso: &mut Escolhas, sentido: Sentido, escolha: &str) {
    let Some(alvo) = alvo_de(sentido, escolha.to_owned()).await else { return };
    if em_uso.de(sentido) == alvo {
        return;
    }
    match trocar(audio, sentido, &alvo) {
        Ok(()) => {
            eprintln!("{} em {alvo}", sentido.nome());
            em_uso.definir(sentido, alvo);
        }
        Err(e) => eprintln!("não consegui levar a {} para {alvo}: {e}", sentido.nome()),
    }
}

async fn alvo_de(sentido: Sentido, escolha: String) -> Option<String> {
    tokio::task::spawn_blocking(move || alvo_vivo(sentido, &escolha)).await.ok().flatten()
}

fn alvo_vivo(sentido: Sentido, escolha: &str) -> Option<String> {
    if !escolha.is_empty() {
        let vivo = aparelhos::listar(sentido).is_ok_and(|lista| lista.iter().any(|aparelho| aparelho.id == escolha));
        if vivo {
            return Some(escolha.to_owned());
        }
        eprintln!("aparelho escolhido indisponível ({escolha}); usando o padrão");
    }
    aparelhos::padrao(sentido)
}

fn trocar(audio: &PlatformAudio, sentido: Sentido, id: &str) -> AudioResult<()> {
    match sentido {
        Sentido::Entrada => audio.switch_recording_device(&RecordingDeviceId::from_unchecked_guid(id)),
        Sentido::Saida => audio.switch_playout_device(&PlayoutDeviceId::from_unchecked_guid(id)),
    }
}
