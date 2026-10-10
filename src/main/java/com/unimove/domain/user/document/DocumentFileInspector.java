package com.unimove.domain.user.document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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

    /** Marcadores de conteudo ativo em PDF: script, execucao externa, anexos, formularios dinamicos. */
    private static final List<String> PDF_ACTIVE_MARKERS = List.of(
            "/JavaScript", "/JS ", "/JS(", "/JS<", "/JS/", "/Launch", "/EmbeddedFile", "/RichMedia", "/XFA");

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

    /**
     * Neutraliza o arquivo antes de guardar:
     * <ul>
     *   <li>imagem: decodifica e regrava os pixels — some EXIF (inclusive GPS
     *       da foto, dado pessoal que nao pedimos) e qualquer carga escondida
     *       em arquivo poliglota; imagem que nao decodifica e recusada;</li>
     *   <li>PDF: recusa o que tem conteudo ativo. Limitacao: nome dentro de
     *       object stream comprimido nao e visto — sem antivirus no MVP.</li>
     * </ul>
     */
    static byte[] sanitize(byte[] content, Detected detected) {
        if ("application/pdf".equals(detected.contentType())) {
            String text = new String(content, StandardCharsets.ISO_8859_1);
            for (String marker : PDF_ACTIVE_MARKERS) {
                if (text.contains(marker)) {
                    throw new InvalidDocumentFileException(
                            "PDF com conteúdo ativo (script/anexo) não é aceito. Envie uma foto ou um PDF simples.");
                }
            }
            return content;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new InvalidDocumentFileException("Imagem corrompida ou ilegível.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, detected.extension().equals("png") ? "png" : "jpg", out)) {
                throw new InvalidDocumentFileException("Formato de imagem não suportado.");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidDocumentFileException("Imagem corrompida ou ilegível.");
        }
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length
                && Arrays.equals(Arrays.copyOf(content, magic.length), magic);
    }
}
