package com.unimove.domain.user.document;

/** Documentos exigidos do motorista. Selfie so aceita imagem; os demais tambem PDF. */
public enum DriverDocumentType {
    CNH(true),
    CRLV(true),
    SELFIE(false);

    private final boolean acceptsPdf;

    DriverDocumentType(boolean acceptsPdf) {
        this.acceptsPdf = acceptsPdf;
    }

    public boolean acceptsPdf() {
        return acceptsPdf;
    }
}
