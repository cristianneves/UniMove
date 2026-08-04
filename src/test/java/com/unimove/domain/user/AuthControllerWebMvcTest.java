package com.unimove.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.domain.user.dto.SocialAuthResponse;
import com.unimove.domain.user.dto.SocialLoginRequest;
import com.unimove.domain.user.dto.SocialRegisterRequest;
import com.unimove.domain.user.social.InvalidSocialTokenException;
import com.unimove.domain.user.social.SocialLoginUnavailableException;
import com.unimove.domain.user.social.SocialProvider;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @MockBean SocialAuthService socialAuthService;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean DriverService driverService;

    @Test
    void registerReturns201WithBody() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "p@example.com", "senha12345", "Maria", "token-verificado",
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
                // Token válido de propósito: o 400 aqui tem de vir da falta de veículo.
                "m@example.com", "senha12345", "João", "token-verificado",
                Role.MOTORISTA, "Campinas", null, null
        );

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void registerAsAdminReturns400AndNeverReachesService() throws Exception {
        RegisterRequest req = new RegisterRequest(
                // Token válido de propósito: o 400 aqui tem de vir da role ADMIN.
                "hacker@example.com", "senha12345", "Invasor", "token-verificado",
                Role.ADMIN, "Campinas", null, null
        );

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").exists());

        verify(authService, never()).register(any());
    }

    @Test
    void registerWithUnknownRoleReturns400() throws Exception {
        String unknownRoleJson = """
                {"email":"x@example.com","password":"senha12345","name":"X",
                 "role":"SUPERADMIN","cidade":"Campinas"}
                """;

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownRoleJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(authService, never()).register(any());
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

    @Test
    void socialLoginReturns200WithToken() throws Exception {
        AuthResponse auth = new AuthResponse("jwt-token", UUID.randomUUID(),
                Role.PASSAGEIRO, "remanso", Instant.now().plusSeconds(3600));
        when(socialAuthService.login(any())).thenReturn(SocialAuthResponse.authenticated(auth));

        mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SocialLoginRequest(SocialProvider.GOOGLE, "id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.auth.token").value("jwt-token"))
                .andExpect(jsonPath("$.profile").doesNotExist());
    }

    @Test
    void socialLoginForNewUserReturns200AskingForRegistration() throws Exception {
        when(socialAuthService.login(any()))
                .thenReturn(SocialAuthResponse.registrationRequired("maria@example.com", "Maria"));

        mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SocialLoginRequest(SocialProvider.GOOGLE, "id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.auth").doesNotExist())
                .andExpect(jsonPath("$.profile.email").value("maria@example.com"));
    }

    @Test
    void socialLoginWithInvalidTokenReturns401() throws Exception {
        when(socialAuthService.login(any())).thenThrow(new InvalidSocialTokenException());

        mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SocialLoginRequest(SocialProvider.GOOGLE, "id-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void socialLoginWithoutProviderConfiguredReturns503() throws Exception {
        when(socialAuthService.login(any())).thenThrow(new SocialLoginUnavailableException());

        mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SocialLoginRequest(SocialProvider.GOOGLE, "id-token"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void socialRegisterReturns201() throws Exception {
        AuthResponse resp = new AuthResponse("jwt-token", UUID.randomUUID(),
                Role.MOTORISTA, "remanso", Instant.now().plusSeconds(3600));
        when(socialAuthService.register(any())).thenReturn(resp);

        SocialRegisterRequest req = new SocialRegisterRequest(SocialProvider.GOOGLE, "id-token",
                "token-verificado", Role.MOTORISTA, "Remanso", VehicleType.MOTO, "ABC1D23");

        mvc.perform(post("/auth/social/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void socialRegisterWithoutPhoneVerificationReturns400() throws Exception {
        String semVerificacao = """
                {"provider":"GOOGLE","idToken":"id-token","role":"PASSAGEIRO","cidade":"Remanso"}
                """;

        mvc.perform(post("/auth/social/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semVerificacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.verificationToken").exists());

        // O provedor social substitui a senha, nunca a verificação do telefone.
        verify(socialAuthService, never()).register(any());
    }

    @Test
    void socialRegisterAsAdminReturns400AndNeverReachesService() throws Exception {
        SocialRegisterRequest req = new SocialRegisterRequest(SocialProvider.GOOGLE, "id-token",
                "token-verificado", Role.ADMIN, "Remanso", null, null);

        mvc.perform(post("/auth/social/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());

        verify(socialAuthService, never()).register(any());
    }
}
