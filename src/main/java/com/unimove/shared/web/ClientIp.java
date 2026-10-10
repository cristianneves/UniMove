package com.unimove.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/** IP do cliente para rate limit e trilhas de acesso. */
public final class ClientIp {

    private ClientIp() {}

    /**
     * Atras do proxy do PaaS o IP real vem em X-Forwarded-For; o primeiro
     * item da lista e o cliente. Cai para o IP da conexao quando ausente.
     */
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
