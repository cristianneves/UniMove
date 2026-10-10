package com.unimove.shared.security;

import com.unimove.domain.city.CityCatalog;
import com.unimove.domain.city.CityController;
import com.unimove.domain.ride.AdminRideController;
import com.unimove.domain.ride.RideAccessDeniedException;
import com.unimove.domain.ride.RideController;
import com.unimove.domain.ride.RideService;
import com.unimove.domain.ride.RideShareService;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.shared.config.SecurityConfig;
import com.unimove.shared.exception.GlobalExceptionHandler;
import com.unimove.shared.security.AccountStatusChecker.AccountState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadeia de seguranca real (SecurityConfig + filtro JWT + @PreAuthorize):
 * 401 sem/token invalido, 403 por role, 403 de conta suspensa com token ainda
 * valido, 401 de conta excluida e 403 de acesso cruzado (BOLA) vindo do service.
 * O resto da suite desliga os filtros; este teste existe para cobrir o que
 * eles fazem.
 */
@WebMvcTest(controllers = {RideController.class, AdminRideController.class, CityController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
class SecurityWebMvcTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    @MockBean AccountStatusChecker accountStatusChecker;
    @MockBean RideService rideService;
    @MockBean RideShareService rideShareService;
    @MockBean CityCatalog cityCatalog;
    // Exigido pelo LastSeenInterceptor, registrado no WebMvcConfig da aplicacao.
    @MockBean DriverService driverService;

    private User passageiro;
    private User motorista;

    @BeforeEach
    void setUp() {
        passageiro = user(Role.PASSAGEIRO);
        motorista = user(Role.MOTORISTA);
        when(accountStatusChecker.stateOf(any(), any())).thenReturn(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("sem token → 401")
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/rides/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Autenticação necessária"));
    }

    @Test
    @DisplayName("token adulterado → 401")
    void tamperedTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/rides/history").header("Authorization", "Bearer " + token(passageiro) + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("passageiro no mural do motorista → 403")
    void passengerCannotReadMural() throws Exception {
        mvc.perform(get("/rides/mural").header("Authorization", bearer(passageiro)))
                .andExpect(status().isForbidden());
        verify(rideService, never()).listMural(any());
    }

    @Test
    @DisplayName("motorista criando corrida → 403")
    void driverCannotCreateRide() throws Exception {
        mvc.perform(post("/rides").header("Authorization", bearer(motorista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latOrigem\":-9.6,\"lngOrigem\":-42.1,\"latDestino\":-9.61,\"lngDestino\":-42.11}"))
                .andExpect(status().isForbidden());
        verify(rideService, never()).create(any(), any());
    }

    @Test
    @DisplayName("passageiro em rota de admin → 403")
    void passengerCannotReachAdmin() throws Exception {
        mvc.perform(get("/admin/rides").header("Authorization", bearer(passageiro)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("conta suspensa com token ainda valido → 403 com motivo, sem chegar ao service")
    void suspendedAccountIsBlockedImmediately() throws Exception {
        when(accountStatusChecker.stateOf(eq(passageiro.getId()), any())).thenReturn(AccountState.SUSPENDED);

        mvc.perform(get("/rides/history").header("Authorization", bearer(passageiro)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Conta suspensa. Entre em contato com o suporte para mais informações."));
        verify(rideService, never()).history(any(), any(), any());
    }

    @Test
    @DisplayName("conta excluida com token ainda valido → 401")
    void deletedAccountIsUnauthorized() throws Exception {
        when(accountStatusChecker.stateOf(eq(passageiro.getId()), any())).thenReturn(AccountState.GONE);

        mvc.perform(get("/rides/history").header("Authorization", bearer(passageiro)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sessão inválida: a conta não existe mais."));
    }

    @Test
    @DisplayName("rota publica continua acessivel com token de conta bloqueada")
    void publicRouteIgnoresBlockedToken() throws Exception {
        when(accountStatusChecker.stateOf(eq(passageiro.getId()), any())).thenReturn(AccountState.SUSPENDED);
        when(cityCatalog.listActive()).thenReturn(List.of());

        mvc.perform(get("/cities").header("Authorization", bearer(passageiro)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("token anterior a troca de senha → 401 pedindo novo login")
    void revokedTokenIsUnauthorized() throws Exception {
        when(accountStatusChecker.stateOf(eq(passageiro.getId()), any())).thenReturn(AccountState.REVOKED);

        mvc.perform(get("/rides/history").header("Authorization", bearer(passageiro)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sessão encerrada por alteração na conta (senha ou cidade). Entre novamente."));
    }

    @Test
    @DisplayName("senha temporaria: rotas comuns → 403 pedindo troca")
    void temporaryPasswordBlocksOtherRoutes() throws Exception {
        when(accountStatusChecker.stateOf(eq(passageiro.getId()), any()))
                .thenReturn(AccountState.PASSWORD_CHANGE_REQUIRED);

        mvc.perform(get("/rides/history").header("Authorization", bearer(passageiro)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Troque a senha temporária antes de continuar (PUT /users/me/password)."));
        verify(rideService, never()).history(any(), any(), any());
    }

    @Test
    @DisplayName("corrida de outro usuario (BOLA) → 403 vindo do service")
    void crossAccountRideAccessIsForbidden() throws Exception {
        UUID rideId = UUID.randomUUID();
        when(rideService.get(any(), eq(rideId))).thenThrow(new RideAccessDeniedException());

        mvc.perform(get("/rides/" + rideId).header("Authorization", bearer(passageiro)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("token valido e conta ativa → passa e o principal chega ao controller")
    void activeAccountReachesController() throws Exception {
        mvc.perform(get("/rides/history").header("Authorization", bearer(passageiro)))
                .andExpect(status().isOk());
        verify(rideService).history(eq(new AuthenticatedUser(
                passageiro.getId(), passageiro.getEmail(), Role.PASSAGEIRO, passageiro.getCidade())), any(), any());
    }

    private String bearer(User user) {
        return "Bearer " + token(user);
    }

    private String token(User user) {
        return jwtService.generate(user).token();
    }

    private static User user(Role role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(role.name().toLowerCase() + "@x.com");
        u.setRole(role);
        u.setCidade("cidade-a");
        return u;
    }
}
