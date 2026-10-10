package com.unimove.domain.user.document;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Supabase Storage dos documentos de motorista.
 *
 * @param url                  URL do projeto (https://&lt;ref&gt;.supabase.co). Vazio desliga o upload (503).
 * @param serviceKey           chave service_role — so no backend, nunca no app.
 * @param bucket               bucket PRIVADO (criar sem "public").
 * @param signedUrlTtlSeconds  vida da URL assinada entregue ao admin.
 * @param maxFileBytes         teto por arquivo.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage.supabase")
public record DocumentStorageProperties(
        @DefaultValue("") String url,
        @DefaultValue("") String serviceKey,
        @DefaultValue("driver-documents") String bucket,
        @DefaultValue("300") @Positive long signedUrlTtlSeconds,
        @DefaultValue("5242880") @Positive long maxFileBytes
) {
    public boolean configured() {
        return !url.isBlank() && !serviceKey.isBlank();
    }
}
