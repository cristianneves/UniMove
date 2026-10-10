package com.unimove.domain.user;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.user.dto.UpdatePreferencesRequest;
import com.unimove.domain.user.dto.PrivacyPreferencesResponse;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserPrivacyController.class, LegalController.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({com.unimove.shared.exception.GlobalExceptionHandler.class, UserPrivacyControllerWebMvcTest.Props.class})
class UserPrivacyControllerWebMvcTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class Props {
        @org.springframework.context.annotation.Bean
        PrivacyProperties privacyProperties() {
            return new PrivacyProperties("2026-10", "https://unimove.app/privacidade");
        }
    }

    @Autowired MockMvc mvc;

    @MockBean AccountPrivacyService service;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean DriverService driverService;

    private AuthenticatedUser user;

    @BeforeEach
    void authenticate() {
        user = new AuthenticatedUser(UUID.randomUUID(), "u@x.com", Role.PASSAGEIRO, "remanso-ba");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /legal/terms → versao vigente e link da politica")
    void legalTerms() throws Exception {
        mvc.perform(get("/legal/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2026-10"))
                .andExpect(jsonPath("$.policyUrl").value("https://unimove.app/privacidade"));
    }

    @Test
    @DisplayName("DELETE /users/me com senha → 204")
    void deleteWithPassword() throws Exception {
        mvc.perform(delete("/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"segredo\"}"))
                .andExpect(status().isNoContent());
        verify(service).deleteAccount(user, "segredo");
    }

    @Test
    @DisplayName("DELETE /users/me sem corpo (conta social) → 204")
    void deleteWithoutBody() throws Exception {
        mvc.perform(delete("/users/me")).andExpect(status().isNoContent());
        verify(service).deleteAccount(eq(user), isNull());
    }

    @Test
    @DisplayName("DELETE com corrida ativa → 409")
    void deleteBlocked() throws Exception {
        doThrow(AccountDeletionBlockedException.activeRide()).when(service).deleteAccount(any(), any());

        mvc.perform(delete("/users/me")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /users/me/preferences sem marketingOptIn → 400")
    void preferencesValidation() throws Exception {
        mvc.perform(put("/users/me/preferences").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).updatePreferences(any(), any());
    }

    @Test
    @DisplayName("PUT /users/me/preferences → 200 com transacional sempre ligado")
    void preferencesUpdate() throws Exception {
        when(service.updatePreferences(user, new UpdatePreferencesRequest(true, PaymentMethod.PIX, null)))
                .thenReturn(new PrivacyPreferencesResponse(
                        true, null, true, "2026-10", null, "2026-10", true, PaymentMethod.PIX, null));

        mvc.perform(put("/users/me/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketingOptIn\":true,\"preferredPaymentMethod\":\"PIX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionalNotifications").value(true))
                .andExpect(jsonPath("$.preferredPaymentMethod").value("PIX"));
    }

    @Test
    @DisplayName("PUT /users/me/preferences com categoria inexistente → 400")
    void preferencesInvalidEnum() throws Exception {
        mvc.perform(put("/users/me/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketingOptIn\":true,\"preferredCategory\":\"ONIBUS\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).updatePreferences(any(), any());
    }

    @Test
    @DisplayName("GET /users/me/data-export → 200")
    void export() throws Exception {
        mvc.perform(get("/users/me/data-export")).andExpect(status().isOk());
        verify(service).export(user);
    }
}
