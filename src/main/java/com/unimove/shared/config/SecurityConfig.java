package com.unimove.shared.config;

import com.unimove.shared.exception.ApiError;
import com.unimove.shared.security.AccountStatusChecker;
import com.unimove.shared.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Despacho ASYNC/ERROR e continuacao de um request ja autorizado
                        // (fim de stream SSE, pagina de erro). Reautorizar ali falha com
                        // a resposta ja enviada quando o acesso foi revogado no meio.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/auth/**",
                                // Chamado pela Meta, nao por um usuario logado. A autenticacao
                                // e a assinatura HMAC do payload, conferida no proprio controller.
                                "/webhooks/whatsapp",
                                "/share/**",
                                // Lista de cidades atendidas: o app precisa dela
                                // para montar o seletor da tela de cadastro,
                                // antes de existir qualquer token.
                                "/cities",
                                // Versao e link da politica de privacidade: exibidos
                                // na tela de cadastro, antes de existir token.
                                "/legal/**",
                                // Swagger: cadeia propria com login de admin (DocsSecurityConfig).
                                // Health (status + probes) e versao do build: lidos pelo
                                // deploy, pelo monitor externo e pelo scripts/smoke.sh.
                                "/actuator/health", "/actuator/health/**", "/actuator/info"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> writeAuthenticationError(req, res))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, req.getRequestURI(),
                                HttpStatus.FORBIDDEN, "Acesso negado"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * 401 padrao, exceto quando o JWT era valido mas a conta foi bloqueada
     * depois da emissao: suspensa vira 403 com a mesma mensagem do login, para
     * o app mostrar o motivo em vez de so pedir login de novo.
     */
    private void writeAuthenticationError(jakarta.servlet.http.HttpServletRequest req,
                                          jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
        Object blocked = req.getAttribute(JwtAuthenticationFilter.BLOCKED_ACCOUNT_ATTRIBUTE);
        if (blocked == AccountStatusChecker.AccountState.SUSPENDED) {
            writeError(res, req.getRequestURI(), HttpStatus.FORBIDDEN,
                    "Conta suspensa. Entre em contato com o suporte para mais informações.");
        } else if (blocked == AccountStatusChecker.AccountState.PASSWORD_CHANGE_REQUIRED) {
            writeError(res, req.getRequestURI(), HttpStatus.FORBIDDEN,
                    "Troque a senha temporária antes de continuar (PUT /users/me/password).");
        } else if (blocked == AccountStatusChecker.AccountState.REVOKED) {
            writeError(res, req.getRequestURI(), HttpStatus.UNAUTHORIZED,
                    "Sessão encerrada por alteração na conta (senha ou cidade). Entre novamente.");
        } else if (blocked == AccountStatusChecker.AccountState.GONE) {
            writeError(res, req.getRequestURI(), HttpStatus.UNAUTHORIZED,
                    "Sessão inválida: a conta não existe mais.");
        } else {
            writeError(res, req.getRequestURI(), HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        }
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res, String path,
                            HttpStatus status, String message) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, path);
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
