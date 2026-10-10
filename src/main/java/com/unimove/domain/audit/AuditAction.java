package com.unimove.domain.audit;

/** Acoes administrativas auditadas. O alvo (target) diz sobre o que a acao incidiu. */
public enum AuditAction {
    DRIVER_APPROVED,
    DRIVER_DOCUMENT_APPROVED,
    DRIVER_DOCUMENT_REJECTED,
    DRIVER_DOCUMENT_VIEWED,
    DRIVER_DOCUMENTATION_VIEWED,
    USER_SUSPENDED,
    USER_REACTIVATED,
    USER_PASSWORD_RESET,
    PRICING_UPSERTED,
    PRICING_DELETED,
    CITY_OPENED,
    CITY_DEACTIVATED,
    RIDE_CANCELLED,
    CAMPAIGN_SENT
}
