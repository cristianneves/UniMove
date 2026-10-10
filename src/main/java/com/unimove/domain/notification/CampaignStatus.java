package com.unimove.domain.notification;

public enum CampaignStatus {
    /** Entregue na caixa de entrada de todos os destinatarios elegiveis. */
    SENT,
    /** Ninguem elegivel no segmento (sem opt-in) — registrada, nada entregue. */
    NO_AUDIENCE
}
