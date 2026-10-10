package com.unimove.domain.user.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileInspectorTest {

    static final byte[] JPEG = image("jpg");
    static final byte[] PNG = image("png");
    static final byte[] PDF = "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    /** Imagem real e minima: o upload agora decodifica e regrava os pixels. */
    static byte[] image(String format) {
        try {
            BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            img.setRGB(1, 1, 0xFF0000);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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

    @Test
    @DisplayName("imagem e regravada: decodifica de novo e descarta o que vinha depois dos pixels (carga poliglota)")
    void imageIsReencoded() throws IOException {
        byte[] polyglot = concat(JPEG, "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.US_ASCII));

        byte[] clean = DocumentFileInspector.sanitize(polyglot, DocumentFileInspector.detect(polyglot, true));

        assertThat(ImageIO.read(new ByteArrayInputStream(clean))).isNotNull();
        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("<?php");
    }

    @Test
    @DisplayName("imagem com cabecalho valido mas corrompida → 400")
    void corruptImageRejected() {
        byte[] corrupt = Arrays.copyOf(JPEG, 8);
        assertThatThrownBy(() -> DocumentFileInspector.sanitize(corrupt, DocumentFileInspector.detect(corrupt, true)))
                .isInstanceOf(InvalidDocumentFileException.class);
    }

    @Test
    @DisplayName("PDF com JavaScript/Launch/anexo → 400; PDF simples passa intacto")
    void pdfActiveContent() {
        byte[] js = "%PDF-1.7\n<< /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >>".getBytes(StandardCharsets.US_ASCII);
        byte[] launch = "%PDF-1.4\n<< /S /Launch /F (cmd.exe) >>".getBytes(StandardCharsets.US_ASCII);
        DocumentFileInspector.Detected pdf = DocumentFileInspector.detect(PDF, true);

        assertThatThrownBy(() -> DocumentFileInspector.sanitize(js, pdf)).isInstanceOf(InvalidDocumentFileException.class);
        assertThatThrownBy(() -> DocumentFileInspector.sanitize(launch, pdf)).isInstanceOf(InvalidDocumentFileException.class);
        assertThat(DocumentFileInspector.sanitize(PDF, pdf)).isEqualTo(PDF);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
