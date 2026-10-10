package com.unimove.domain.user.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFileInspectorTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("detecta JPEG, PNG e PDF pelo conteudo")
    void detectsByMagicBytes() {
        assertThat(DocumentFileInspector.detect(JPEG, true).contentType()).isEqualTo("image/jpeg");
        assertThat(DocumentFileInspector.detect(PNG, true).extension()).isEqualTo("png");
        assertThat(DocumentFileInspector.detect(PDF, true).contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("PDF recusado onde so imagem e aceita (selfie)")
    void pdfRejectedWhenNotAccepted() {
        assertThat(DocumentFileInspector.detect(PDF, false)).isNull();
    }

    @Test
    @DisplayName("executavel/HTML disfarcado de imagem e recusado")
    void disguisedFileRejected() {
        assertThat(DocumentFileInspector.detect("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1), true)).isNull();
        assertThat(DocumentFileInspector.detect("<html><script>".getBytes(StandardCharsets.UTF_8), true)).isNull();
        assertThat(DocumentFileInspector.detect(new byte[]{(byte) 0xFF}, true)).isNull();
    }
}
