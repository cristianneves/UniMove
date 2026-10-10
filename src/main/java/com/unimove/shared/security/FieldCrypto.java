package com.unimove.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Criptografia de campo para dado pessoal sensivel (CPF, CNH): AES-256-GCM
 * com IV aleatorio por valor, e HMAC-SHA256 deterministico ("fingerprint")
 * para unicidade/busca sem guardar o valor em claro.
 *
 * <p>Chave em {@code DATA_ENCRYPTION_KEY} (32 bytes em base64). Em producao a
 * ausencia derruba o boot; fora dela cai numa chave fixa de desenvolvimento,
 * com aviso. Trocar a chave torna ilegivel o que ja foi gravado — rotacao
 * exige recifrar (fora do MVP).
 */
@Component
public class FieldCrypto {

    private static final Logger log = LoggerFactory.getLogger(FieldCrypto.class);
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public FieldCrypto(@Value("${app.security.data-encryption-key:}") String base64Key, Environment environment) {
        this(resolveKey(base64Key, Arrays.asList(environment.getActiveProfiles()).contains("prod")));
    }

    FieldCrypto(byte[] key) {
        if (key.length != 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY precisa ter 32 bytes (base64 de 256 bits).");
        }
        this.aesKey = new SecretKeySpec(key, "AES");
        // Chave do HMAC derivada da mesma raiz, mas distinta da de cifragem.
        this.hmacKey = new SecretKeySpec(sha256(concat("hmac:".getBytes(StandardCharsets.UTF_8), key)), "HmacSHA256");
    }

    private static byte[] resolveKey(String base64Key, boolean prod) {
        if (base64Key != null && !base64Key.isBlank()) {
            return Base64.getDecoder().decode(base64Key.trim());
        }
        if (prod) {
            throw new IllegalStateException(
                    "DATA_ENCRYPTION_KEY nao definido em producao. Gere com: openssl rand -base64 32");
        }
        log.warn("DATA_ENCRYPTION_KEY ausente: usando chave fixa de DESENVOLVIMENTO para CPF/CNH.");
        return sha256("unimove-dev-only-data-key".getBytes(StandardCharsets.UTF_8));
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length)
                    .put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar campo", e);
        }
    }

    /** Valor sem o prefixo e legado em claro (gravado antes da V31): devolvido como esta. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao decifrar campo (chave trocada?)", e);
        }
    }

    /** HMAC deterministico: mesmo valor → mesmo fingerprint; sem a chave nao se reverte nem se testa CPF. */
    public String fingerprint(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular fingerprint", e);
        }
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        return ByteBuffer.allocate(a.length + b.length).put(a).put(b).array();
    }
}
