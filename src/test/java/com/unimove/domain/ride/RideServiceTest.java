package com.unimove.domain.ride;

import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.EstimateRequest;
import com.unimove.domain.ride.dto.EstimateResponse;
import com.unimove.domain.ride.dto.RatingResponse;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.ride.dto.SubmitRatingRequest;
import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.RatingStats;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.UserRatingService;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link RideService} com Mockito.
 *
 * Cobertura: máquina de estados, regras de role no cancelamento, gating do driver-location,
 * delegação do mural para o repository, e a invariante crítica de que o preço é calculado a
 * partir do OSRM (não dos campos do request).
 *
 * Lock otimista NÃO é exercitado aqui — depende do {@code @Version} do Hibernate em runtime
 * e do {@code GlobalExceptionHandler} traduzir para 409. Confiamos no schema + no handler.
 */
@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock RideRepository rideRepository;
    @Mock RideRatingRepository rideRatingRepository;
    @Mock MapsService mapsService;
    @Mock PricingPolicy pricingPolicy;
    @Mock PaymentService paymentService;
    @Mock DriverService driverService;
    @Mock UserRatingService userRatingService;

    @InjectMocks RideService rideService;

    private static final String CIDADE = "cidade-a";
    private static final String OUTRA_CIDADE = "cidade-b";

    private AuthenticatedUser pax;
    private AuthenticatedUser mot;
    private AuthenticatedUser outroPax;

    @BeforeEach
    void setUp() {
        pax = new AuthenticatedUser(UUID.randomUUID(), "pax@x.com", Role.PASSAGEIRO, CIDADE);
        mot = new AuthenticatedUser(UUID.randomUUID(), "mot@x.com", Role.MOTORISTA, CIDADE);
        outroPax = new AuthenticatedUser(UUID.randomUUID(), "other@x.com", Role.PASSAGEIRO, CIDADE);
    }

    // ------------------------------------------------------------------------
    // create()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("create: preço vem do OSRM + PricingPolicy, não do request")
    void createComputesPriceFromOsrmNotFromRequest() {
        when(mapsService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteInfo(new BigDecimal("5.000"), 12));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(12)))
                .thenReturn(new BigDecimal("18.40"));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        RideResponse resp = rideService.create(pax, defaultRequest());

        assertThat(resp.status()).isEqualTo(RideStatus.PENDING_PAYMENT);
        assertThat(resp.preco()).isEqualByComparingTo("18.40");
        assertThat(resp.cidade()).isEqualTo(CIDADE);
        assertThat(resp.passageiroId()).isEqualTo(pax.userId());

        ArgumentCaptor<Ride> saved = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(saved.capture());
        assertThat(saved.getValue().getDistanciaKm()).isEqualByComparingTo("5.000");
        assertThat(saved.getValue().getTempoMin()).isEqualTo(12);
    }

    // ------------------------------------------------------------------------
    // estimate()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("estimate: usa OSRM + PricingPolicy e NÃO persiste ride")
    void estimateReturnsPriceWithoutPersisting() {
        when(mapsService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteInfo(new BigDecimal("3.500"), 9));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(9)))
                .thenReturn(new BigDecimal("14.65"));

        EstimateResponse resp = rideService.estimate(new EstimateRequest(
                new BigDecimal("-20.82000"),
                new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"),
                new BigDecimal("-49.39000")
        ));

        assertThat(resp.distanciaKm()).isEqualByComparingTo("3.500");
        assertThat(resp.tempoMin()).isEqualTo(9);
        assertThat(resp.preco()).isEqualByComparingTo("14.65");
        verify(rideRepository, never()).save(any(Ride.class));
    }

    // ------------------------------------------------------------------------
    // confirmPayment()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("confirmPayment PIX → AVAILABLE_IN_MURAL + gera BR Code")
    void confirmPaymentPixGeneratesPayloadAndPublishesToMural() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(paymentService.generatePixPayload(eq(ride.getId()), any(BigDecimal.class)))
                .thenReturn("BR-CODE-FAKE");

        RideResponse resp = rideService.confirmPayment(pax, ride.getId(),
                new ConfirmPaymentRequest(PaymentMethod.PIX));

        assertThat(resp.status()).isEqualTo(RideStatus.AVAILABLE_IN_MURAL);
        assertThat(resp.pixPayload()).isEqualTo("BR-CODE-FAKE");
    }

    @Test
    @DisplayName("confirmPayment DINHEIRO → AVAILABLE_IN_MURAL sem payload Pix")
    void confirmPaymentDinheiroSkipsPixPayload() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        RideResponse resp = rideService.confirmPayment(pax, ride.getId(),
                new ConfirmPaymentRequest(PaymentMethod.DINHEIRO));

        assertThat(resp.status()).isEqualTo(RideStatus.AVAILABLE_IN_MURAL);
        assertThat(resp.pixPayload()).isNull();
        verify(paymentService, never()).generatePixPayload(any(), any());
    }

    @Test
    @DisplayName("confirmPayment por outro passageiro → RideAccessDeniedException")
    void confirmPaymentByNonOwnerFails() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.confirmPayment(outroPax, ride.getId(),
                new ConfirmPaymentRequest(PaymentMethod.PIX)))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    // listMural() + accept()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("listMural delega para o repository com a cidade do motorista")
    void listMuralCallsAuthThenRepositoryWithMotoristaCidade() {
        when(rideRepository.findMural(CIDADE)).thenReturn(java.util.List.of());

        rideService.listMural(mot);

        verify(driverService).assertCanAcceptRides(mot.userId());
        verify(rideRepository).findMural(CIDADE);
    }

    @Test
    @DisplayName("accept: transição AVAILABLE_IN_MURAL → DRIVER_EN_ROUTE + acceptedAt")
    void acceptTransitionsAndStampsAcceptedAt() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        Instant before = Instant.now();
        RideResponse resp = rideService.accept(mot, ride.getId());

        assertThat(resp.status()).isEqualTo(RideStatus.DRIVER_EN_ROUTE);
        assertThat(resp.motoristaId()).isEqualTo(mot.userId());
        assertThat(resp.acceptedAt()).isAfterOrEqualTo(before);
        verify(driverService).assertCanAcceptRides(mot.userId());
    }

    @Test
    @DisplayName("accept de outra cidade → DriverCityMismatchException")
    void acceptFailsForDifferentCity() {
        Ride ride = rideAvailable(pax.userId(), OUTRA_CIDADE);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.accept(mot, ride.getId()))
                .isInstanceOf(DriverCityMismatchException.class);
    }

    @Test
    @DisplayName("accept fora de AVAILABLE_IN_MURAL → IllegalRideTransitionException")
    void acceptFailsIfNotAvailable() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.accept(mot, ride.getId()))
                .isInstanceOf(IllegalRideTransitionException.class);
    }

    // ------------------------------------------------------------------------
    // start() / complete()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("start → IN_PROGRESS + startedAt")
    void startTransitionsAndStampsStartedAt() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        RideResponse resp = rideService.start(mot, ride.getId());

        assertThat(resp.status()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(resp.startedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete → COMPLETED + completedAt")
    void completeTransitionsAndStampsCompletedAt() {
        Ride ride = rideInProgress(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        RideResponse resp = rideService.complete(mot, ride.getId());

        assertThat(resp.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(resp.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("start por motorista que não aceitou → RideAccessDeniedException")
    void startByNonAcceptingDriverFails() {
        Ride ride = rideEnRoute(pax.userId(), UUID.randomUUID());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.start(mot, ride.getId()))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    // cancel() — regras por role
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("passageiro cancela em PENDING_PAYMENT sem motivo")
    void passageiroCancelsPendingPayment() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        RideResponse resp = rideService.cancel(pax, ride.getId(), null);

        assertThat(resp.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(resp.cancelledBy()).isEqualTo(CancelledBy.PASSAGEIRO);
    }

    @Test
    @DisplayName("passageiro cancela em DRIVER_EN_ROUTE")
    void passageiroCancelsAfterAccept() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        RideResponse resp = rideService.cancel(pax, ride.getId(), null);

        assertThat(resp.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(resp.cancelledBy()).isEqualTo(CancelledBy.PASSAGEIRO);
    }

    @Test
    @DisplayName("motorista cancela em DRIVER_EN_ROUTE com motivo")
    void motoristaCancelsWithReason() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        RideResponse resp = rideService.cancel(mot, ride.getId(), new CancelRideRequest("Pneu furou"));

        assertThat(resp.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(resp.cancelledBy()).isEqualTo(CancelledBy.MOTORISTA);
        assertThat(resp.cancelReason()).isEqualTo("Pneu furou");
    }

    @Test
    @DisplayName("motorista sem motivo → MissingCancelReasonException (null e blank)")
    void motoristaCancelWithoutReasonFails() {
        Ride ride1 = rideEnRoute(pax.userId(), mot.userId());
        Ride ride2 = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride1.getId())).thenReturn(Optional.of(ride1));
        when(rideRepository.findById(ride2.getId())).thenReturn(Optional.of(ride2));

        assertThatThrownBy(() -> rideService.cancel(mot, ride1.getId(), null))
                .isInstanceOf(MissingCancelReasonException.class);
        assertThatThrownBy(() -> rideService.cancel(mot, ride2.getId(), new CancelRideRequest("   ")))
                .isInstanceOf(MissingCancelReasonException.class);
    }

    @Test
    @DisplayName("motorista não pode cancelar fora de DRIVER_EN_ROUTE")
    void motoristaCannotCancelInProgress() {
        Ride ride = rideInProgress(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancel(mot, ride.getId(), new CancelRideRequest("desisti")))
                .isInstanceOf(IllegalRideTransitionException.class);
    }

    @Test
    @DisplayName("passageiro não pode cancelar em IN_PROGRESS")
    void passageiroCannotCancelInProgress() {
        Ride ride = rideInProgress(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancel(pax, ride.getId(), null))
                .isInstanceOf(IllegalRideTransitionException.class);
    }

    @Test
    @DisplayName("outro passageiro tentando cancelar → RideAccessDeniedException")
    void cancelByOtherPassengerFails() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancel(outroPax, ride.getId(), null))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    // updateDriverLocation() — gating por status
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("driver-location em DRIVER_EN_ROUTE retorna distância Haversine")
    void driverLocationOnEnRouteComputesDistance() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        UpdateDriverLocationRequest loc = new UpdateDriverLocationRequest(
                new BigDecimal("-20.82100"), new BigDecimal("-49.37900"));

        RideResponse resp = rideService.updateDriverLocation(mot, ride.getId(), loc);

        assertThat(resp.driverCurrentLat()).isEqualByComparingTo(loc.lat());
        assertThat(resp.driverDistanceKm()).isNotNull();
    }

    @Test
    @DisplayName("driver-location em AVAILABLE_IN_MURAL → LocationUpdateNotAllowedException")
    void driverLocationBlockedBeforeEnRoute() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        ride.setMotoristaId(mot.userId()); // estado inconsistente proposital pra forçar o gate de status
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.updateDriverLocation(mot, ride.getId(),
                new UpdateDriverLocationRequest(new BigDecimal("-20.0"), new BigDecimal("-49.0"))))
                .isInstanceOf(LocationUpdateNotAllowedException.class);
    }

    @Test
    @DisplayName("driver-location por motorista diferente → RideAccessDeniedException")
    void driverLocationByWrongDriverFails() {
        Ride ride = rideEnRoute(pax.userId(), UUID.randomUUID());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.updateDriverLocation(mot, ride.getId(),
                new UpdateDriverLocationRequest(new BigDecimal("-20.0"), new BigDecimal("-49.0"))))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    // get() — controle de acesso
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("get: passageiro dono e motorista aceitante enxergam; outros levam 403")
    void getEnforcesAccessControl() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(userRatingService.getStats(mot.userId()))
                .thenReturn(new RatingStats(new BigDecimal("4.50"), 10));

        RideResponse fromPax = rideService.get(pax, ride.getId());
        assertThat(fromPax.id()).isEqualTo(ride.getId());
        assertThat(fromPax.motoristaRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(fromPax.motoristaRatingCount()).isEqualTo(10);

        assertThat(rideService.get(mot, ride.getId()).id()).isEqualTo(ride.getId());

        AuthenticatedUser intruso = new AuthenticatedUser(UUID.randomUUID(),
                "x@x.com", Role.MOTORISTA, CIDADE);
        assertThatThrownBy(() -> rideService.get(intruso, ride.getId()))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    @Test
    @DisplayName("get de ride inexistente → RideNotFoundException")
    void getRideNotFound() {
        UUID id = UUID.randomUUID();
        when(rideRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.get(pax, id))
                .isInstanceOf(RideNotFoundException.class);
    }

    // ------------------------------------------------------------------------
    // submitRating()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("submitRating: passageiro avalia motorista após COMPLETED")
    void submitRatingByPassengerHappyPath() {
        Ride ride = rideCompleted(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(rideRatingRepository.existsByRideIdAndRaterId(ride.getId(), pax.userId())).thenReturn(false);
        when(rideRatingRepository.save(any(RideRating.class))).thenAnswer(inv -> {
            RideRating r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            if (r.getCreatedAt() == null) r.setCreatedAt(Instant.now());
            return r;
        });
        when(userRatingService.applyRating(mot.userId(), 5))
                .thenReturn(new RatingStats(new BigDecimal("4.80"), 25));

        RatingResponse resp = rideService.submitRating(pax, ride.getId(),
                new SubmitRatingRequest(5, "Excelente"));

        assertThat(resp.raterId()).isEqualTo(pax.userId());
        assertThat(resp.rateeId()).isEqualTo(mot.userId());
        assertThat(resp.score()).isEqualTo(5);
        assertThat(resp.comment()).isEqualTo("Excelente");
        assertThat(resp.rateeNewRatingAvg()).isEqualByComparingTo("4.80");
        assertThat(resp.rateeNewRatingCount()).isEqualTo(25);
    }

    @Test
    @DisplayName("submitRating: motorista avalia passageiro após COMPLETED")
    void submitRatingByDriverHappyPath() {
        Ride ride = rideCompleted(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(rideRatingRepository.existsByRideIdAndRaterId(ride.getId(), mot.userId())).thenReturn(false);
        when(rideRatingRepository.save(any(RideRating.class))).thenAnswer(inv -> {
            RideRating r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            if (r.getCreatedAt() == null) r.setCreatedAt(Instant.now());
            return r;
        });
        when(userRatingService.applyRating(pax.userId(), 4))
                .thenReturn(new RatingStats(new BigDecimal("4.20"), 3));

        RatingResponse resp = rideService.submitRating(mot, ride.getId(),
                new SubmitRatingRequest(4, null));

        assertThat(resp.raterId()).isEqualTo(mot.userId());
        assertThat(resp.rateeId()).isEqualTo(pax.userId());
        assertThat(resp.score()).isEqualTo(4);
    }

    @Test
    @DisplayName("submitRating antes de COMPLETED → RatingNotAllowedException")
    void submitRatingBlockedBeforeCompleted() {
        Ride ride = rideInProgress(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.submitRating(pax, ride.getId(),
                new SubmitRatingRequest(5, null)))
                .isInstanceOf(RatingNotAllowedException.class);
    }

    @Test
    @DisplayName("submitRating duplicado → RatingAlreadySubmittedException")
    void submitRatingDuplicateFails() {
        Ride ride = rideCompleted(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(rideRatingRepository.existsByRideIdAndRaterId(ride.getId(), pax.userId())).thenReturn(true);

        assertThatThrownBy(() -> rideService.submitRating(pax, ride.getId(),
                new SubmitRatingRequest(5, null)))
                .isInstanceOf(RatingAlreadySubmittedException.class);
    }

    @Test
    @DisplayName("submitRating por parte não envolvida → RideAccessDeniedException")
    void submitRatingByOtherUserFails() {
        Ride ride = rideCompleted(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.submitRating(outroPax, ride.getId(),
                new SubmitRatingRequest(5, null)))
                .isInstanceOf(RideAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    // Builders auxiliares
    // ------------------------------------------------------------------------

    private static CreateRideRequest defaultRequest() {
        return new CreateRideRequest(
                new BigDecimal("-20.82000"),
                new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"),
                new BigDecimal("-49.39000")
        );
    }

    private Ride ridePending(UUID passageiroId) {
        Ride r = baseRide(passageiroId, CIDADE);
        r.setStatus(RideStatus.PENDING_PAYMENT);
        return r;
    }

    private Ride rideAvailable(UUID passageiroId, String cidade) {
        Ride r = baseRide(passageiroId, cidade);
        r.setStatus(RideStatus.AVAILABLE_IN_MURAL);
        return r;
    }

    private Ride rideEnRoute(UUID passageiroId, UUID motoristaId) {
        Ride r = baseRide(passageiroId, CIDADE);
        r.setStatus(RideStatus.DRIVER_EN_ROUTE);
        r.setMotoristaId(motoristaId);
        r.setAcceptedAt(Instant.now());
        return r;
    }

    private Ride rideInProgress(UUID passageiroId, UUID motoristaId) {
        Ride r = rideEnRoute(passageiroId, motoristaId);
        r.setStatus(RideStatus.IN_PROGRESS);
        r.setStartedAt(Instant.now());
        return r;
    }

    private Ride rideCompleted(UUID passageiroId, UUID motoristaId) {
        Ride r = rideInProgress(passageiroId, motoristaId);
        r.setStatus(RideStatus.COMPLETED);
        r.setCompletedAt(Instant.now());
        return r;
    }

    private Ride baseRide(UUID passageiroId, String cidade) {
        Ride r = new Ride();
        r.setId(UUID.randomUUID());
        r.setPassageiroId(passageiroId);
        r.setCidade(cidade);
        r.setLatOrigem(new BigDecimal("-20.82000"));
        r.setLngOrigem(new BigDecimal("-49.38000"));
        r.setLatDestino(new BigDecimal("-20.83000"));
        r.setLngDestino(new BigDecimal("-49.39000"));
        r.setDistanciaKm(new BigDecimal("5.000"));
        r.setTempoMin(12);
        r.setPreco(new BigDecimal("18.40"));
        r.setCreatedAt(Instant.now());
        return r;
    }
}
