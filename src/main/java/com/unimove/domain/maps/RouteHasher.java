package com.unimove.domain.maps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class RouteHasher {

    private RouteHasher() {}

    static String hash(double latOrigem, double lngOrigem, double latDestino, double lngDestino) {
        return hash(List.of(
                new GeoPoint(latOrigem, lngOrigem),
                new GeoPoint(latDestino, lngDestino)));
    }

    /**
     * Hash determinístico da sequencia ordenada de waypoints. Para 2 pontos a chave
     * gerada e identica a versao antiga (round(latO)|round(lngO)|round(latD)|round(lngD)),
     * o que preserva os registros ja existentes em route_cache.
     */
    static String hash(List<GeoPoint> waypoints) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < waypoints.size(); i++) {
            if (i > 0) {
                key.append('|');
            }
            GeoPoint p = waypoints.get(i);
            key.append(round(p.lat())).append('|').append(round(p.lng()));
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel na JVM", e);
        }
    }

    private static String round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
