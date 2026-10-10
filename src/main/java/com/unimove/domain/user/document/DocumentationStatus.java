package com.unimove.domain.user.document;

/** Situacao consolidada da documentacao do motorista (derivada, nao persistida). */
public enum DocumentationStatus {
    /** Falta dado declarado ou algum dos tres documentos nunca foi enviado. */
    INCOMPLETE,
    /** Algum documento reprovado aguarda reenvio. */
    REJECTED,
    /** Tudo enviado; ha documento aguardando analise do admin. */
    PENDING_REVIEW,
    /** Tudo aprovado, mas a CNH venceu. */
    EXPIRED,
    /** Tudo aprovado e CNH valida — pode ser aprovado/operar. */
    APPROVED
}
