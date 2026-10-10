package com.unimove.domain.user.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato HTTP com a API do Supabase Storage, sem rede (ExchangeFunction fake). */
class SupabaseDocumentStorageTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    private SupabaseDocumentStorage storage(HttpStatus status, String body) {
        WebClient client = WebClient.builder()
                .exchangeFunction(req -> {
                    requests.add(req);
                    return Mono.just(ClientResponse.create(status)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .build();
        return new SupabaseDocumentStorage(client, "https://proj.supabase.co/", "driver-documents");
    }

    @Test
    @DisplayName("put: POST no objeto do bucket, sem upsert, com a barra do caminho preservada")
    void putPostsObject() {
        storage(HttpStatus.OK, "{}").put("drv/cnh/doc.jpg", new byte[]{1}, "image/jpeg");

        ClientRequest req = requests.get(0);
        assertThat(req.method()).isEqualTo(HttpMethod.POST);
        assertThat(req.url().toString())
                .isEqualTo("https://proj.supabase.co/storage/v1/object/driver-documents/drv/cnh/doc.jpg");
        assertThat(req.headers().getFirst("x-upsert")).isEqualTo("false");
        assertThat(req.headers().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    @DisplayName("signedUrl: monta a URL completa a partir do signedURL relativo")
    void signedUrlBuildsAbsoluteUrl() {
        DocumentStorage.SignedUrl signed = storage(HttpStatus.OK,
                "{\"signedURL\":\"/object/sign/driver-documents/drv/cnh/doc.jpg?token=abc\"}")
                .signedUrl("drv/cnh/doc.jpg", Duration.ofSeconds(300));

        assertThat(requests.get(0).url().toString())
                .isEqualTo("https://proj.supabase.co/storage/v1/object/sign/driver-documents/drv/cnh/doc.jpg");
        assertThat(signed.url()).isEqualTo(
                "https://proj.supabase.co/storage/v1/object/sign/driver-documents/drv/cnh/doc.jpg?token=abc");
    }

    @Test
    @DisplayName("delete: DELETE no bucket com a lista de prefixos")
    void deleteSendsPrefixes() {
        storage(HttpStatus.OK, "[]").delete(List.of("a/b.jpg"));

        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.DELETE);
        assertThat(requests.get(0).url().toString())
                .isEqualTo("https://proj.supabase.co/storage/v1/object/driver-documents");
    }

    @Test
    @DisplayName("delete de lista vazia nao chama a API")
    void deleteEmptyIsNoop() {
        storage(HttpStatus.OK, "[]").delete(List.of());

        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("erro da API vira 503 de storage indisponivel")
    void apiErrorBecomesUnavailable() {
        assertThatThrownBy(() -> storage(HttpStatus.BAD_REQUEST, "{\"error\":\"Bucket not found\"}")
                .put("x/y.jpg", new byte[]{1}, "image/jpeg"))
                .isInstanceOf(DocumentStorageUnavailableException.class);
    }
}
