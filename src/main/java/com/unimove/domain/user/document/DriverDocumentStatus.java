package com.unimove.domain.user.document;

/**
 * Estado de UM envio. "Rascunho" e a ausencia de envio; a analise e feita
 * por um unico papel (ADMIN), entao nao ha "em analise" separado de SUBMITTED.
 */
public enum DriverDocumentStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
