use livekit::DisconnectReason;

pub fn tipo_da_queda(motivo: DisconnectReason) -> &'static str {
    if adianta_voltar(motivo) { "queda" } else { "fim" }
}

fn adianta_voltar(motivo: DisconnectReason) -> bool {
    !matches!(
        motivo,
        DisconnectReason::ClientInitiated
            | DisconnectReason::DuplicateIdentity
            | DisconnectReason::ParticipantRemoved
            | DisconnectReason::RoomDeleted
            | DisconnectReason::RoomClosed
            | DisconnectReason::UserRejected
            | DisconnectReason::UserUnavailable
    )
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn queda_de_rede_pede_para_voltar() {
        assert_eq!(tipo_da_queda(DisconnectReason::SignalClose), "queda");
        assert_eq!(tipo_da_queda(DisconnectReason::ConnectionTimeout), "queda");
        assert_eq!(tipo_da_queda(DisconnectReason::UnknownReason), "queda");
    }

    #[test]
    fn saida_pedida_ou_expulsao_encerra() {
        assert_eq!(tipo_da_queda(DisconnectReason::ClientInitiated), "fim");
        assert_eq!(tipo_da_queda(DisconnectReason::DuplicateIdentity), "fim");
        assert_eq!(tipo_da_queda(DisconnectReason::RoomDeleted), "fim");
        assert_eq!(tipo_da_queda(DisconnectReason::UserRejected), "fim");
    }
}
