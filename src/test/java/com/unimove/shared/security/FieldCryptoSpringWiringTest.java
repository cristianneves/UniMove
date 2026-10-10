package com.unimove.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressao: com dois construtores o Spring nao sabia qual usar e o boot
 * caia (pego so ao subir a API contra o Postgres). Garante que o container
 * consegue montar a FieldCrypto e o converter do JPA.
 */
class FieldCryptoSpringWiringTest {

    @Test
    @DisplayName("Spring instancia FieldCrypto e EncryptedStringConverter")
    void springCanWire() {
        try (var ctx = new AnnotationConfigApplicationContext(FieldCrypto.class, EncryptedStringConverter.class)) {
            EncryptedStringConverter converter = ctx.getBean(EncryptedStringConverter.class);
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("x"))).isEqualTo("x");
        }
    }
}
