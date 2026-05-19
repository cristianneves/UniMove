package com.unimove.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.domain.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(com.unimove.shared.exception.GlobalExceptionHandler.class)
class AuthControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AuthService authService;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void registerReturns201WithBody() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "p@example.com", "senha12345", "Maria", null,
                Role.PASSAGEIRO, "Campinas", null, null
        );
        AuthResponse resp = new AuthResponse("jwt-token", UUID.randomUUID(),
                Role.PASSAGEIRO, "campinas", Instant.now().plusSeconds(3600));
        when(authService.register(any())).thenReturn(resp);

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("PASSAGEIRO"));
    }

    @Test
    void registerWithInvalidPayloadReturns400WithFieldErrors() throws Exception {
        String invalidJson = """
                {"email":"nao-eh-email","password":"123","name":"","role":"PASSAGEIRO","cidade":""}
                """;

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void registerMotoristaWithoutVehicleReturns400() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "m@example.com", "senha12345", "João", null,
                Role.MOTORISTA, "Campinas", null, null
        );

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void loginReturns200WithBody() throws Exception {
        AuthResponse resp = new AuthResponse("jwt-token", UUID.randomUUID(),
                Role.PASSAGEIRO, "campinas", Instant.now().plusSeconds(3600));
        when(authService.login(any())).thenReturn(resp);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("p@example.com", "senha12345"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("p@example.com", "senha12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }
}
