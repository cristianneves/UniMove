package com.unimove.shared.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /** Motivo pelo qual um JWT valido nao autenticou; lido pelo entry point do SecurityConfig. */
    public static final String BLOCKED_ACCOUNT_ATTRIBUTE = "unimove.blockedAccount";

    private final JwtService jwtService;
    private final AccountStatusChecker accountStatusChecker;

    public JwtAuthenticationFilter(JwtService jwtService, AccountStatusChecker accountStatusChecker) {
        this.jwtService = jwtService;
        this.accountStatusChecker = accountStatusChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        AuthenticatedUser principal;
        Instant issuedAt;
        try {
            JwtService.ParsedToken parsed = jwtService.parseToken(token);
            principal = parsed.user();
            issuedAt = parsed.issuedAt();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT inválido: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        // Uma consulta por PK por request: o preco de revogar na hora o acesso
        // de quem foi suspenso ou excluiu a conta, sem blacklist de tokens.
        // A conta bloqueada so deixa de autenticar: rota publica segue
        // funcionando (ex.: recadastro com token velho no app) e a protegida
        // cai no entry point, que le o motivo deste atributo.
        AccountStatusChecker.AccountState state = accountStatusChecker.stateOf(principal.userId(), issuedAt);
        // Senha temporaria: autentica so para trocar a senha e ler o perfil.
        if (state == AccountStatusChecker.AccountState.PASSWORD_CHANGE_REQUIRED && allowedWhileTemporary(request)) {
            state = AccountStatusChecker.AccountState.ACTIVE;
        }
        if (state != AccountStatusChecker.AccountState.ACTIVE) {
            request.setAttribute(BLOCKED_ACCOUNT_ATTRIBUTE, state);
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private static boolean allowedWhileTemporary(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("PUT".equals(request.getMethod()) && "/users/me/password".equals(path))
                || ("GET".equals(request.getMethod()) && "/users/me".equals(path));
    }
}
