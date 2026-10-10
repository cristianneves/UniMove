package com.unimove.domain.user.document;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Armazenamento PRIVADO dos arquivos de documento. Nenhum arquivo tem URL
 * publica: a leitura e sempre por URL assinada de vida curta, emitida so para
 * o admin (e auditada).
 */
public interface DocumentStorage {

    void put(String path, byte[] content, String contentType);

    SignedUrl signedUrl(String path, Duration ttl);

    /** Idempotente: caminho inexistente nao e erro. */
    void delete(List<String> paths);

    record SignedUrl(String url, Instant expiresAt) {}
}
