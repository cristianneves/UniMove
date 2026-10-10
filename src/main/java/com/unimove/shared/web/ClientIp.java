package com.unimove.shared.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** IP do cliente para rate limit e trilhas de acesso. */
public final class ClientIp {

    private ClientIp() {}

    /**
     * Atras do proxy do PaaS o IP real vem em X-Forwarded-For. Cada proxy
     * acrescenta a direita o IP de quem falou com ele, entao o primeiro IP
     * publico lido da direita para a esquerda e o do cliente; as entradas a
     * esquerda dele vieram no header do proprio cliente e sao forjaveis (usar
     * a primeira deixaria trocar de "IP" a cada request e escapar do rate
     * limit). Saltos privados (proxies internos da plataforma) sao pulados —
     * senao todo mundo teria o IP do balanceador.
     */
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String innermost = null;
            for (int i = hops.length - 1; i >= 0; i--) {
                String ip = hops[i].trim();
                if (ip.isEmpty()) {
                    continue;
                }
                if (innermost == null) {
                    innermost = ip;
                }
                if (!isInternal(ip)) {
                    return ip;
                }
            }
            if (innermost != null) {
                return innermost;
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isInternal(String ip) {
        // So literais de IP: nunca deixar InetAddress resolver nome (DNS).
        if (!ip.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            byte first = addr.getAddress()[0];
            boolean ipv6UniqueLocal = addr.getAddress().length == 16 && (first & 0xFE) == 0xFC;
            return addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                    || ipv6UniqueLocal;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
