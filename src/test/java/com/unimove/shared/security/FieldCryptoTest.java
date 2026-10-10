package com.unimove.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCryptoTest {

    private static final byte[] KEY = new byte[32];
    private final FieldCrypto crypto = new FieldCrypto(KEY);

    @Test
    @DisplayName("cifra e decifra; o banco nunca ve o valor em claro")
    void roundTrip() {
        String stored = crypto.encrypt("52998224725");

        assertThat(stored).startsWith("v1:").doesNotContain("52998224725");
        assertThat(crypto.decrypt(stored)).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("IV aleatorio: o mesmo CPF gera textos cifrados diferentes")
    void randomIv() {
        assertThat(crypto.encrypt("52998224725")).isNotEqualTo(crypto.encrypt("52998224725"));
    }

    @Test
    @DisplayName("fingerprint e deterministico (serve para indice unico) e depende da chave")
    void fingerprint() {
        assertThat(crypto.fingerprint("52998224725")).isEqualTo(crypto.fingerprint("52998224725")).hasSize(64);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        assertThat(new FieldCrypto(otherKey).fingerprint("52998224725")).isNotEqualTo(crypto.fingerprint("52998224725"));
    }

    @Test
    @DisplayName("valor legado em claro (antes da V31) e lido como esta; nulos passam")
    void legacyPlaintext() {
        assertThat(crypto.decrypt("52998224725")).isEqualTo("52998224725");
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("chave errada nao decifra (GCM autentica)")
    void wrongKeyFails() {
        byte[] otherKey = new byte[32];
        otherKey[31] = 9;
        String stored = crypto.encrypt("12345678901");

        assertThatThrownBy(() -> new FieldCrypto(otherKey).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prod sem DATA_ENCRYPTION_KEY derruba o boot; com chave valida sobe")
    void prodRequiresKey() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new FieldCrypto("", prod)).isInstanceOf(IllegalStateException.class);
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        assertThat(new FieldCrypto(key, prod).decrypt(new FieldCrypto(key, prod).encrypt("x"))).isEqualTo("x");
    }

    @Test
    @DisplayName("chave de tamanho errado e recusada")
    void wrongKeySize() {
        assertThatThrownBy(() -> new FieldCrypto(new byte[16])).isInstanceOf(IllegalStateException.class);
    }
}
