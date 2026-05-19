package com.unimove.domain.maps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RouteHasher {

    private RouteHasher() {}

    static String hash(double latOrigem, double lngOrigem, double latDestino, double lngDestino) {
        String key = round(latOrigem) + "|" + round(lngOrigem) + "|"
                + round(latDestino) + "|" + round(lngDestino);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel na JVM", e);
        }
    }

    private static String round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
