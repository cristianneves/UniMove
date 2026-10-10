package com.unimove.domain.user.document;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

/**
 * Sem {@code app.storage.supabase.url}/{@code service-key} o storage nasce
 * "indisponivel": dev e testes sobem sem credencial e o upload responde 503,
 * mesma estrategia do login social.
 */
@Configuration
@EnableConfigurationProperties(DocumentStorageProperties.class)
class DocumentStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageConfig.class);

    @Bean
    DocumentStorage documentStorage(DocumentStorageProperties props) {
        if (!props.configured()) {
            log.warn("Storage de documentos nao configurado (app.storage.supabase.*): upload de documentos respondera 503.");
            return new UnavailableDocumentStorage();
        }
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(20));
        int maxInMemory = (int) Math.min(Integer.MAX_VALUE, props.maxFileBytes() + 64 * 1024);
        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(maxInMemory))
                        .build())
                .defaultHeader("Authorization", "Bearer " + props.serviceKey())
                .defaultHeader("apikey", props.serviceKey())
                .build();
        return new SupabaseDocumentStorage(client, props.url(), props.bucket());
    }

    static final class UnavailableDocumentStorage implements DocumentStorage {
        @Override
        public void put(String path, byte[] content, String contentType) {
            throw new DocumentStorageUnavailableException();
        }

        @Override
        public SignedUrl signedUrl(String path, Duration ttl) {
            throw new DocumentStorageUnavailableException();
        }

        @Override
        public void delete(List<String> paths) {
            if (!paths.isEmpty()) {
                throw new DocumentStorageUnavailableException();
            }
        }
    }
}
