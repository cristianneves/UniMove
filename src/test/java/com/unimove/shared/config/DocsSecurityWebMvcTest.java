package com.unimove.shared.config;

import com.unimove.domain.user.AuthService;
import com.unimove.domain.user.DocsAuthenticationProvider;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.domain.user.UserStatus;
import com.unimove.shared.security.AccountStatusChecker;
import com.unimove.shared.security.JwtAuthenticationFilter;
import com.unimove.shared.security.JwtService;
import com.unimove.shared.web.DocsLoginController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadeia da documentacao: Swagger so com sessao de admin, login por formulario. */
@WebMvcTest(controllers = DocsLoginController.class)
@ActiveProfiles("test")
@Import({DocsSecurityConfig.class, SecurityConfig.class, DocsAuthenticationProvider.class,
        JwtAuthenticationFilter.class, JwtService.class})
class DocsSecurityWebMvcTest {

    @Autowired MockMvc mvc;

    @MockBean AuthService authService;
    @MockBean AccountStatusChecker accountStatusChecker;
    // Exigido pelo LastSeenInterceptor, registrado no WebMvcConfig da aplicacao.
    @MockBean DriverService driverService;

    private static User account(Role role, boolean mustChangePassword) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(role.name().toLowerCase() + "@x.com");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePassword(mustChangePassword);
        return u;
    }

    @Test
    @DisplayName("swagger sem sessao → redirect relativo para a tela de login")
    void swaggerRequiresLogin() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/docs/login"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("tela de login e publica e traz o token CSRF")
    void loginPageRenders() throws Exception {
        mvc.perform(get("/docs/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    @DisplayName("admin entra e vai para o swagger")
    void adminLogsIn() throws Exception {
        when(authService.authenticate("admin@x.com", "senha")).thenReturn(account(Role.ADMIN, false));

        mvc.perform(post("/docs/login").with(csrf()).param("email", "admin@x.com").param("password", "senha"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }

    @Test
    @DisplayName("passageiro com senha certa nao entra")
    void nonAdminIsRejected() throws Exception {
        when(authService.authenticate("p@x.com", "senha")).thenReturn(account(Role.PASSAGEIRO, false));

        mvc.perform(post("/docs/login").with(csrf()).param("email", "p@x.com").param("password", "senha"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/docs/login?error"));
    }

    @Test
    @DisplayName("admin com senha temporaria nao entra")
    void temporaryPasswordIsRejected() throws Exception {
        when(authService.authenticate("admin@x.com", "temp")).thenReturn(account(Role.ADMIN, true));

        mvc.perform(post("/docs/login").with(csrf()).param("email", "admin@x.com").param("password", "temp"))
                .andExpect(header().string("Location", "/docs/login?error"));
    }

    @Test
    @DisplayName("senha errada nao entra")
    void wrongPasswordIsRejected() throws Exception {
        when(authService.authenticate("admin@x.com", "errada")).thenThrow(new BadCredentialsException("x"));

        mvc.perform(post("/docs/login").with(csrf()).param("email", "admin@x.com").param("password", "errada"))
                .andExpect(header().string("Location", "/docs/login?error"));
    }

    @Test
    @DisplayName("POST de login sem CSRF → 403")
    void loginRequiresCsrf() throws Exception {
        mvc.perform(post("/docs/login").param("email", "admin@x.com").param("password", "senha"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sessao de nao-admin no swagger → 403")
    void nonAdminSessionIsForbidden() throws Exception {
        mvc.perform(get("/v3/api-docs").with(user("p@x.com").roles("PASSAGEIRO")))
                .andExpect(status().isForbidden());
    }
}
