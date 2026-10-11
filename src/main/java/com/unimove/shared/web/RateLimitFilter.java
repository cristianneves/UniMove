package com.unimove.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimove.shared.exception.ApiError;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teto de requests por janela fixa nas rotas caras ou abusaveis que nao tem
 * protecao propria:
 * <ul>
 *   <li><b>auth</b> — login/cadastro/social, por IP. Complementa o lockout por
 *       e-mail do login (que nao segura quem varia o e-mail) e protege o
 *       BCrypt do cadastro.</li>
 *   <li><b>maps</b> — geocode/reverse, por usuario (ou IP sem token). O
 *       Photon publico tem politica de uso justo; um app em loop nao pode
 *       queimar a cota da cidade inteira.</li>
 * </ul>
 * O desafio do WhatsApp ja tem seu proprio limitador (ChallengeRateLimiter).
 *
 * <p>Estado em memoria, mesma escolha (e mesma limitacao com multiplas
 * replicas) documentada em {@code LoginAttemptService}. Roda depois da cadeia
 * do Spring Security, entao o usuario autenticado ja esta no contexto.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final Set<String> AUTH_PATHS = Set.of(
            "/auth/login", "/auth/register", "/auth/social", "/auth/social/register", "/auth/password/reset", "/docs/login");
    private static final String MAPS_PREFIX = "/maps/";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Rule auth;
    private final Rule maps;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    record Rule(int maxRequests, Duration window) {}

    private record Window(int count, Instant startedAt) {}

    @Autowired
    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.auth.max-requests:30}") int authMax,
                           @Value("${app.rate-limit.auth.window-minutes:10}") int authWindowMinutes,
                           @Value("${app.rate-limit.maps.max-requests:60}") int mapsMax,
                           @Value("${app.rate-limit.maps.window-minutes:1}") int mapsWindowMinutes) {
        this(objectMapper, Clock.systemUTC(),
                new Rule(authMax, Duration.ofMinutes(authWindowMinutes)),
                new Rule(mapsMax, Duration.ofMinutes(mapsWindowMinutes)));
    }

    RateLimitFilter(ObjectMapper objectMapper, Clock clock, Rule auth, Rule maps) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.auth = auth;
        this.maps = maps;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && AUTH_PATHS.contains(path)) {
            if (!allow("auth:" + ClientIp.of(request), auth, response, request)) {
                return;
            }
        } else if ("GET".equals(request.getMethod()) && path.startsWith(MAPS_PREFIX)) {
            if (!allow("maps:" + callerKey(request), maps, response, request)) {
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, Rule rule, HttpServletResponse response,
                          HttpServletRequest request) throws IOException {
        Instant now = clock.instant();
        Window w = windows.compute(key, (k, prev) -> {
            if (prev == null || !now.isBefore(prev.startedAt().plus(rule.window()))) {
                return new Window(1, now);
            }
            return new Window(prev.count() + 1, prev.startedAt());
        });
        if (w.count() <= rule.maxRequests()) {
            return true;
        }
        long retryAfter = Math.max(1, Duration.between(now, w.startedAt().plus(rule.window())).toSeconds());
        log.warn("Rate limit estourado: chave={} ({} requests na janela)", key, w.count());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Muitas requisições. Tente novamente em " + retryAfter + " segundos.",
                request.getRequestURI()));
        return false;
    }

    private static String callerKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.userId();
        }
        return "ip:" + ClientIp.of(request);
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-interval-ms:600000}")
    void purgeStaleWindows() {
        Instant now = clock.instant();
        Duration longest = auth.window().compareTo(maps.window()) >= 0 ? auth.window() : maps.window();
        windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().startedAt().plus(longest)));
    }
}
