package com.unimove.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    @Test
    @DisplayName("usa a entrada mais a direita do X-Forwarded-For (a do proxy), nao a forjavel")
    void rightmostForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "6.6.6.6, 1.2.3.4");
        assertThat(ClientIp.of(req)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("pula os saltos internos (proxy da plataforma) a direita")
    void skipsInternalHops() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "6.6.6.6, 1.2.3.4, 10.0.0.1, 172.16.5.4");
        assertThat(ClientIp.of(req)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("so IPs internos: usa o mais proximo (nao quebra em rede local)")
    void onlyInternal() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "192.168.0.2, 10.0.0.1");
        assertThat(ClientIp.of(req)).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("sem X-Forwarded-For usa o IP da conexao")
    void fallbackRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("9.9.9.9");
        assertThat(ClientIp.of(req)).isEqualTo("9.9.9.9");
    }
}
