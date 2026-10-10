package com.unimove.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Documentacao da API (Swagger) so para admin, com login proprio por sessao.
 * Cadeia separada e avaliada antes da cadeia da API: aqui ha sessao e CSRF
 * (formulario de login), la tudo segue stateless por JWT.
 */
@Configuration
public class DocsSecurityConfig {

    static final String LOGIN_PAGE = "/docs/login";

    @Bean
    @Order(1)
    public SecurityFilterChain docsSecurityFilterChain(HttpSecurity http,
                                                       AuthenticationProvider docsAuthenticationProvider)
            throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/docs/**")
                .authenticationProvider(docsAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_PAGE).permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage(LOGIN_PAGE)
                        .loginProcessingUrl(LOGIN_PAGE)
                        .usernameParameter("email")
                        .defaultSuccessUrl("/swagger-ui/index.html", true)
                        .failureUrl(LOGIN_PAGE + "?error"))
                .logout(logout -> logout
                        .logoutUrl("/docs/logout")
                        .logoutSuccessUrl(LOGIN_PAGE + "?logout"))
                // Redirect relativo (o Tomcat nao o torna absoluto): atras do proxy
                // (Caddy) o host/porta que o Tomcat ve nao sao os publicos, e o
                // LoginUrlAuthenticationEntryPoint montaria uma URL absoluta errada.
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        (request, response, ex) -> response.sendRedirect(LOGIN_PAGE)));
        return http.build();
    }
}
