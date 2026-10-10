package com.unimove.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimove.domain.user.Role;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private MutableClock clock;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
        filter = new RateLimitFilter(new ObjectMapper().findAndRegisterModules(), clock,
                new RateLimitFilter.Rule(2, Duration.ofMinutes(10)),
                new RateLimitFilter.Rule(3, Duration.ofMinutes(1)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("auth: estoura o teto por IP → 429 com Retry-After e corpo ApiError")
    void authLimitPerIp() throws Exception {
        assertThat(run("POST", "/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(run("POST", "/auth/register", "1.1.1.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = run("POST", "/auth/login", "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("600");
        assertThat(blocked.getContentAsString()).contains("\"status\":429");
    }

    @Test
    @DisplayName("auth: IPs diferentes tem janelas independentes")
    void authLimitIsPerIp() throws Exception {
        run("POST", "/auth/login", "1.1.1.1");
        run("POST", "/auth/login", "1.1.1.1");

        assertThat(run("POST", "/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("auth: a janela reinicia depois de expirar")
    void authWindowResets() throws Exception {
        run("POST", "/auth/login", "1.1.1.1");
        run("POST", "/auth/login", "1.1.1.1");
        clock.advance(Duration.ofMinutes(10));

        assertThat(run("POST", "/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("maps: limite por usuario autenticado, nao por IP")
    void mapsLimitPerUser() throws Exception {
        authenticate(UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            assertThat(run("GET", "/maps/geocode", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        assertThat(run("GET", "/maps/reverse", "9.9.9.9").getStatus()).isEqualTo(429);

        authenticate(UUID.randomUUID());
        assertThat(run("GET", "/maps/geocode", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("rotas fora das regras nunca sao limitadas")
    void otherRoutesAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(run("GET", "/rides/history", "1.1.1.1").getStatus()).isEqualTo(200);
            assertThat(run("GET", "/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse run(String method, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private static void authenticate(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "u@x.com", Role.PASSAGEIRO, "cidade-a");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
