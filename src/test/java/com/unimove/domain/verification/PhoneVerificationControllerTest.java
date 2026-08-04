package com.unimove.domain.verification;

import com.unimove.domain.verification.dto.ChallengeResponse;
import com.unimove.domain.verification.dto.ChallengeStatusResponse;
import com.unimove.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PhoneVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class PhoneVerificationControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PhoneVerificationService service;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.unimove.domain.user.DriverService driverService;

    @Test
    @DisplayName("criação de desafio devolve 201 com o link wa.me")
    void createChallengeReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.createChallenge(anyString())).thenReturn(new ChallengeResponse(
                id, "ABC12345", "https://wa.me/5574999990000?text=UNIMOVE-ABC12345",
                Instant.parse("2026-08-03T12:10:00Z")));

        mvc.perform(post("/auth/phone/challenge"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeId").value(id.toString()))
                .andExpect(jsonPath("$.code").value("ABC12345"))
                .andExpect(jsonPath("$.waLink").value("https://wa.me/5574999990000?text=UNIMOVE-ABC12345"));
    }

    @Test
    @DisplayName("atrás de proxy o rate limit usa o primeiro IP do X-Forwarded-For, não o do proxy")
    void usesFirstForwardedIpForRateLimiting() throws Exception {
        when(service.createChallenge(anyString())).thenReturn(new ChallengeResponse(
                UUID.randomUUID(), "ABC12345", "https://wa.me/x", Instant.now()));

        mvc.perform(post("/auth/phone/challenge")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(service).createChallenge(ip.capture());
        assertThat(ip.getValue()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("estouro do teto por IP vira 429")
    void rateLimitedReturns429() throws Exception {
        when(service.createChallenge(anyString())).thenThrow(new TooManyChallengesException());

        mvc.perform(post("/auth/phone/challenge"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("status VERIFIED entrega o token de verificação")
    void statusReturnsTokenWhenVerified() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getStatus(id)).thenReturn(new ChallengeStatusResponse(
                PhoneVerificationStatus.VERIFIED, "(74) 9****-0000", "tok-abc", null));

        mvc.perform(get("/auth/phone/challenge/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.phone").value("(74) 9****-0000"))
                .andExpect(jsonPath("$.verificationToken").value("tok-abc"));
    }

    @Test
    @DisplayName("status REJECTED informa o motivo para o app orientar o usuário")
    void statusExposesRejectionReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getStatus(id)).thenReturn(new ChallengeStatusResponse(
                PhoneVerificationStatus.REJECTED, null, null, RejectionReason.PHONE_IN_USE));

        mvc.perform(get("/auth/phone/challenge/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("PHONE_IN_USE"))
                .andExpect(jsonPath("$.verificationToken").doesNotExist());
    }

    @Test
    @DisplayName("desafio inexistente vira 400")
    void unknownChallengeReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getStatus(id)).thenThrow(new PhoneVerificationRequiredException(
                "Desafio de verificacao nao encontrado."));

        mvc.perform(get("/auth/phone/challenge/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
