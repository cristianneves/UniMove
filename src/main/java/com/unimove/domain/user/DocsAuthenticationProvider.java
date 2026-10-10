package com.unimove.domain.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Login da documentacao da API (Swagger). Mesma conferencia do login do app
 * (lockout, BCrypt, conta ativa), mas so admin entra — e admin com senha
 * temporaria troca no app primeiro. A sessao vale so para as rotas da doc;
 * a API continua stateless por JWT.
 */
@Component
public class DocsAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(DocsAuthenticationProvider.class);

    private final AuthService authService;

    public DocsAuthenticationProvider(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        User user;
        try {
            user = authService.authenticate(email, password);
        } catch (TooManyLoginAttemptsException e) {
            throw new LockedException(e.getMessage());
        } catch (UserSuspendedException e) {
            throw new DisabledException(e.getMessage());
        }
        if (user.getRole() != Role.ADMIN) {
            log.warn("Login na documentacao negado para nao-admin: userId={}", user.getId());
            throw new BadCredentialsException("Acesso restrito a administradores.");
        }
        if (user.isMustChangePassword()) {
            throw new DisabledException("Troque a senha temporária antes de acessar a documentação.");
        }
        log.info("Admin {} entrou na documentacao da API", user.getId());
        return UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + Role.ADMIN.name())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
