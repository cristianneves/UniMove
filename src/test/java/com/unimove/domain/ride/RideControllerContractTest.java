package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.EstimateResponse;
import com.unimove.domain.ride.dto.CategoryOption;
import com.unimove.domain.ride.dto.RatingResponse;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.ride.dto.RideRouteResponse;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP Flutter↔Spring dos endpoints de corrida: rota, status, corpo
 * minimo aceito, validacao (400) e o shape do JSON que o app consome. As
 * regras de negocio estao no RideServiceTest; roles/401/403 no
 * SecurityWebMvcTest. Aqui so o "fio".
 */
@WebMvcTest(controllers = RideController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(com.unimove.shared.exception.GlobalExceptionHandler.class)
class RideControllerContractTest {

    private static final String ORIGIN_DEST = """
            {"latOrigem":-9.6300,"lngOrigem":-42.0800,"latDestino":-9.6400,"lngDestino":-42.0900}
            """;

    @Autowired MockMvc mvc;

    @MockBean RideService rideService;
    @MockBean RideShareService rideShareService;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean DriverService driverService;

    private AuthenticatedUser user;
    private final UUID rideId = UUID.randomUUID();

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

    // ------------------------------------------------------------------------
    // passageiro: estimativa, criacao, pagamento
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("POST /rides/estimate → 200 com preco por categoria")
    void estimate() throws Exception {
        when(rideService.estimate(eq(user), any())).thenReturn(new EstimateResponse(
                new BigDecimal("2.500"), 7, new BigDecimal("12.30"), BigDecimal.ONE, "poly",
                List.of(new CategoryOption(RideCategory.MOTO, new BigDecimal("8.10"), BigDecimal.ONE))));

        mvc.perform(post("/rides/estimate").contentType(MediaType.APPLICATION_JSON).content(ORIGIN_DEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preco").value(12.30))
                .andExpect(jsonPath("$.options[0].category").value("MOTO"));
    }

    @Test
    @DisplayName("POST /rides/estimate com latitude fora do intervalo → 400 com campo")
    void estimateValidation() throws Exception {
        mvc.perform(post("/rides/estimate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latOrigem\":-99,\"lngOrigem\":-42,\"latDestino\":-9,\"lngDestino\":-42}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latOrigem").exists());
        verify(rideService, never()).estimate(any(), any());
    }

    @Test
    @DisplayName("POST /rides → 201 com Location e RideResponse")
    void create() throws Exception {
        when(rideService.create(eq(user), any())).thenReturn(ride(RideStatus.PENDING_PAYMENT));

        mvc.perform(post("/rides").contentType(MediaType.APPLICATION_JSON).content(ORIGIN_DEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/rides/" + rideId))
                .andExpect(jsonPath("$.id").value(rideId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("POST /rides com mais de 5 paradas → 400")
    void createTooManyStops() throws Exception {
        String stop = "{\"lat\":-9.63,\"lng\":-42.08}";
        String body = "{\"latOrigem\":-9.63,\"lngOrigem\":-42.08,\"latDestino\":-9.64,\"lngDestino\":-42.09,"
                + "\"stops\":[" + String.join(",", List.of(stop, stop, stop, stop, stop, stop)) + "]}";

        mvc.perform(post("/rides").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /rides com corrida ativa → 409 com mensagem")
    void createConflict() throws Exception {
        when(rideService.create(eq(user), any())).thenThrow(ActiveRideExistsException.forPassenger());

        mvc.perform(post("/rides").contentType(MediaType.APPLICATION_JSON).content(ORIGIN_DEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /rides/{id}/confirm-payment DINHEIRO com troco → 200 com paymentStatus")
    void confirmCash() throws Exception {
        RideResponse resp = ride(RideStatus.AVAILABLE_IN_MURAL);
        when(rideService.confirmPayment(eq(user), eq(rideId), any())).thenReturn(resp);

        mvc.perform(post("/rides/" + rideId + "/confirm-payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"DINHEIRO\",\"changeFor\":50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    @Test
    @DisplayName("confirm-payment com metodo desconhecido → 400 (corpo ilegivel)")
    void confirmUnknownMethod() throws Exception {
        mvc.perform(post("/rides/" + rideId + "/confirm-payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARTAO\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------
    // motorista: mural, aceite, GPS, ciclo, dinheiro
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("GET /rides/mural → lista com troco para o motorista se preparar")
    void mural() throws Exception {
        when(rideService.listMural(user)).thenReturn(List.of(new RideMuralItem(rideId,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 5,
                new BigDecimal("10.00"), RideCategory.CARRO, PaymentMethod.DINHEIRO, new BigDecimal("50.00"),
                Instant.now())));

        mvc.perform(get("/rides/mural"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMethod").value("DINHEIRO"))
                .andExpect(jsonPath("$[0].cashChangeFor").value(50.00));
    }

    @Test
    @DisplayName("POST /rides/{id}/accept sem corpo e aceito (localizacao opcional)")
    void acceptWithoutBody() throws Exception {
        when(rideService.accept(eq(user), eq(rideId), isNull())).thenReturn(ride(RideStatus.DRIVER_EN_ROUTE));

        mvc.perform(post("/rides/" + rideId + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRIVER_EN_ROUTE"));
    }

    @Test
    @DisplayName("POST /rides/{id}/accept com corpo invalido → 400")
    void acceptInvalidBody() throws Exception {
        mvc.perform(post("/rides/" + rideId + "/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":-9.6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lng").exists());
    }

    @Test
    @DisplayName("aceite concorrente perdido → 409")
    void acceptRace() throws Exception {
        when(rideService.accept(eq(user), eq(rideId), isNull()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Ride.class, rideId));

        mvc.perform(post("/rides/" + rideId + "/accept"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT driver-location aceita recordedAt/accuracyM; posicao recusada → 422")
    void driverLocation() throws Exception {
        when(rideService.updateDriverLocation(eq(user), eq(rideId), any()))
                .thenThrow(new InvalidDriverLocationException("Posição desatualizada"));

        mvc.perform(put("/rides/" + rideId + "/driver-location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":-9.63,\"lng\":-42.08,\"recordedAt\":\"2026-10-10T12:00:00Z\",\"accuracyM\":8.5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Posição desatualizada"));
    }

    @Test
    @DisplayName("PUT driver-location sem lat → 400")
    void driverLocationValidation() throws Exception {
        mvc.perform(put("/rides/" + rideId + "/driver-location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lng\":-42.08}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("start/complete → 200; transicao ilegal → 409")
    void startAndComplete() throws Exception {
        when(rideService.start(user, rideId)).thenReturn(ride(RideStatus.IN_PROGRESS));
        when(rideService.complete(user, rideId))
                .thenThrow(new IllegalRideTransitionException(RideStatus.DRIVER_EN_ROUTE, RideStatus.COMPLETED));

        mvc.perform(post("/rides/" + rideId + "/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/rides/" + rideId + "/complete")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST cash-payment → 200; sem valor → 400")
    void cashPayment() throws Exception {
        when(rideService.confirmCashPayment(eq(user), eq(rideId), any())).thenReturn(ride(RideStatus.COMPLETED));

        mvc.perform(post("/rides/" + rideId + "/cash-payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAmount\":12.30}"))
                .andExpect(status().isOk());
        mvc.perform(post("/rides/" + rideId + "/cash-payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receivedAmount").exists());
    }

    // ------------------------------------------------------------------------
    // ambos: cancelamento, leitura, avaliacao, historico
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("POST cancel sem corpo e aceito; motivo longo demais → 400")
    void cancel() throws Exception {
        when(rideService.cancel(eq(user), eq(rideId), isNull())).thenReturn(ride(RideStatus.CANCELLED));

        mvc.perform(post("/rides/" + rideId + "/cancel")).andExpect(status().isOk());
        mvc.perform(post("/rides/" + rideId + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /rides/{id} e /route; id malformado → 400 (nao 500)")
    void getAndRoute() throws Exception {
        when(rideService.get(user, rideId)).thenReturn(ride(RideStatus.IN_PROGRESS));
        when(rideService.getRoute(user, rideId)).thenReturn(new RideRouteResponse("poly"));

        mvc.perform(get("/rides/" + rideId)).andExpect(status().isOk());
        mvc.perform(get("/rides/" + rideId + "/route")).andExpect(jsonPath("$.geometry").value("poly"));
        mvc.perform(get("/rides/nao-e-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST rating → 200; nota fora de 1..5 → 400; avaliar de novo → 409")
    void rating() throws Exception {
        when(rideService.submitRating(eq(user), eq(rideId), any())).thenReturn(new RatingResponse(
                UUID.randomUUID(), rideId, user.userId(), UUID.randomUUID(), 5, null, Instant.now(),
                new BigDecimal("4.90"), 10));

        mvc.perform(post("/rides/" + rideId + "/rating").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateeNewRatingCount").value(10));
        mvc.perform(post("/rides/" + rideId + "/rating").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":6}"))
                .andExpect(status().isBadRequest());

        when(rideService.submitRating(eq(user), eq(rideId), any())).thenThrow(new RatingAlreadySubmittedException());
        mvc.perform(post("/rides/" + rideId + "/rating").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /rides/history pagina e filtra por status; status invalido → 400")
    void history() throws Exception {
        when(rideService.history(eq(user), eq(RideStatus.COMPLETED), any())).thenAnswer(inv -> {
            Pageable p = inv.getArgument(2);
            assertThat(p.getPageNumber()).isEqualTo(1);
            assertThat(p.getPageSize()).isEqualTo(5);
            return new PageImpl<>(List.of(historyItem()), PageRequest.of(1, 5), 6);
        });

        mvc.perform(get("/rides/history?status=COMPLETED&page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].destinoEndereco").value("Rodoviária"))
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/rides/history?status=VOANDO")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /rides/{id}/share-link → 200 com token e path; encerrada → 410")
    void shareLink() throws Exception {
        UUID token = UUID.randomUUID();
        when(rideShareService.getLink(user, rideId))
                .thenReturn(com.unimove.domain.ride.dto.ShareLinkResponse.of(token))
                .thenThrow(new ShareLinkExpiredException());

        mvc.perform(get("/rides/" + rideId + "/share-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token.toString()))
                .andExpect(jsonPath("$.path").value("/share/" + token));
        mvc.perform(get("/rides/" + rideId + "/share-link"))
                .andExpect(status().isGone());
    }

    // ------------------------------------------------------------------------

    private RideResponse ride(RideStatus status) {
        return new RideResponse(rideId, user.userId(), null, "remanso-ba",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, List.of(),
                new BigDecimal("2.500"), 7, new BigDecimal("12.30"), BigDecimal.ONE, RideCategory.CARRO,
                status, PaymentMethod.DINHEIRO, null, PaymentStatus.PENDING, new BigDecimal("50.00"),
                null, null, null, null, null, null, null, null, null, null, Instant.now(),
                null, null, null, null, null, null, null, null);
    }

    private RideHistoryItem historyItem() {
        return new RideHistoryItem(rideId, RideStatus.COMPLETED, "remanso-ba", RideCategory.CARRO,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2.500"), 7,
                new BigDecimal("12.30"), null, PaymentMethod.DINHEIRO, PaymentStatus.CONFIRMED,
                user.userId(), UUID.randomUUID(), Instant.now(), Instant.now(), null,
                "Praça da Matriz", "Rodoviária");
    }
}
