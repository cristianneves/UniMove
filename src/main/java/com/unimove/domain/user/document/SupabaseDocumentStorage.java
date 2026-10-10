package com.unimove.domain.user.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Adapter do Supabase Storage (API REST). Autentica com a service_role, que
 * ignora RLS — por isso o bucket precisa ser privado e a chave so existe no
 * backend.
 */
class SupabaseDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabaseDocumentStorage.class);
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(20);

    private final WebClient client;
    private final String storageBaseUrl;
    private final String bucket;

    /**
     * Caminhos sao montados so com UUID, nome de enum e extensao (ver
     * DriverDocumentService), entao a URI vai concatenada: variavel de
     * template codificaria a "/" do caminho como %2F.
     */
    SupabaseDocumentStorage(WebClient client, String projectUrl, String bucket) {
        this.client = client;
        this.storageBaseUrl = projectUrl.replaceAll("/+$", "") + "/storage/v1";
        this.bucket = bucket;
    }

    @Override
    public void put(String path, byte[] content, String contentType) {
        try {
            client.post()
                    .uri(URI.create(storageBaseUrl + "/object/" + bucket + "/" + path))
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(contentType))
                    .bodyValue(content)
                    .retrieve()
                    .toBodilessEntity()
                    .block(BLOCK_TIMEOUT);
        } catch (RuntimeException e) {
            log.error("Falha no upload de documento para o storage ({}): {}", path, describe(e));
            throw new DocumentStorageUnavailableException();
        }
    }

    @Override
    public SignedUrl signedUrl(String path, Duration ttl) {
        try {
            Map<?, ?> body = client.post()
                    .uri(URI.create(storageBaseUrl + "/object/sign/" + bucket + "/" + path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("expiresIn", ttl.toSeconds()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(BLOCK_TIMEOUT);
            Object signed = body == null ? null : body.get("signedURL");
            if (signed == null) {
                throw new IllegalStateException("resposta sem signedURL");
            }
            return new SignedUrl(storageBaseUrl + signed, Instant.now().plus(ttl));
        } catch (RuntimeException e) {
            log.error("Falha ao assinar URL de documento ({}): {}", path, describe(e));
            throw new DocumentStorageUnavailableException();
        }
    }

    @Override
    public void delete(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        try {
            client.method(HttpMethod.DELETE)
                    .uri(URI.create(storageBaseUrl + "/object/" + bucket))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("prefixes", paths))
                    .retrieve()
                    .toBodilessEntity()
                    .block(BLOCK_TIMEOUT);
        } catch (RuntimeException e) {
            log.error("Falha ao apagar {} documento(s) do storage: {}", paths.size(), describe(e));
            throw new DocumentStorageUnavailableException();
        }
    }

    private static String describe(RuntimeException e) {
        return e instanceof WebClientResponseException w
                ? w.getStatusCode() + " " + w.getResponseBodyAsString()
                : e.toString();
    }
}
