package com.unimove.domain.user.document;

import java.util.Arrays;

/**
 * Descobre o tipo real do arquivo pelos primeiros bytes ("magic numbers").
 * O Content-Type e a extensao enviados pelo cliente sao ignorados: sao
 * controlados por quem envia e nao provam nada.
 */
final class DocumentFileInspector {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    private DocumentFileInspector() {}

    record Detected(String contentType, String extension) {}

    static Detected detect(byte[] content, boolean acceptsPdf) {
        if (startsWith(content, JPEG)) {
            return new Detected("image/jpeg", "jpg");
        }
        if (startsWith(content, PNG)) {
            return new Detected("image/png", "png");
        }
        if (acceptsPdf && startsWith(content, PDF)) {
            return new Detected("application/pdf", "pdf");
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length
                && Arrays.equals(Arrays.copyOf(content, magic.length), magic);
    }
}
