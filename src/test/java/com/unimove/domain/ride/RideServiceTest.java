package com.unimove.domain.ride;

import com.unimove.domain.chat.ChatSseHub;
import com.unimove.domain.maps.GeoPoint;
import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.MapsUnavailableException;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.dto.AcceptRideRequest;
import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.CategoryOption;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.EstimateRequest;
import com.unimove.domain.ride.dto.EstimateResponse;
import com.unimove.domain.ride.dto.RatingResponse;
import com.unimove.domain.ride.dto.RecentDestinationResponse;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.ride.dto.RideStatusEvent;
import com.unimove.domain.ride.dto.StopPoint;
import com.unimove.domain.ride.dto.SubmitRatingRequest;
import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.RatingStats;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.UserAccountService;
import com.unimove.domain.user.UserRatingService;
import com.unimove.domain.user.VehicleType;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock SurgePolicy surgePolicy;
    @Mock CancellationPolicy cancellationPolicy;
    @Mock PaymentService paymentService;
    @Mock DriverService driverService;
    @Mock UserRatingService userRatingService;
    @Mock UserAccountService userAccountService;
    @Mock ChatSseHub chatSseHub;
    @Mock RideStatusSseHub statusSseHub;
    @Mock MuralSseHub muralSseHub;

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

        lenient().when(driverService.getVehicleType(mot.userId())).thenReturn(VehicleType.CARRO);
        lenient().when(cancellationPolicy.computeFee(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        // Surge desligado por default: preco = base. Testes de surge re-stubam.
        lenient().when(surgePolicy.multiplier(anyString(), any(RideCategory.class)))
                .thenReturn(BigDecimal.ONE);
    }

    // ------------------------------------------------------------------------
    // corrida ativa única (create/accept)
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("create: passageiro com corrida ativa → ActiveRideExistsException, sem chamar o OSRM")
    void createBlockedWhenPassengerHasActiveRide() {
        when(rideRepository.existsByPassageiroIdAndStatusIn(eq(pax.userId()), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> rideService.create(pax, defaultRequest()))
                .isInstanceOf(ActiveRideExistsException.class);

        verify(mapsService, never()).route(anyList());
        verify(rideRepository, never()).save(any());
    }

    @Test
    @DisplayName("accept: motorista com corrida ativa → ActiveRideExistsException, sem carregar a corrida")
    void acceptBlockedWhenDriverHasActiveRide() {
        when(rideRepository.existsByMotoristaIdAndStatusIn(eq(mot.userId()), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> rideService.accept(mot, UUID.randomUUID()))
                .isInstanceOf(ActiveRideExistsException.class);

        verify(rideRepository, never()).findByIdFetchingStops(any());
    }

    // ------------------------------------------------------------------------
    // create()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("create: preço vem do OSRM + PricingPolicy, não do request")
    void createComputesPriceFromOsrmNotFromRequest() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("5.000"), 12, "poly_5km"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(12), any(RideCategory.class), anyString()))
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
        assertThat(saved.getValue().getRouteGeometry()).isEqualTo("poly_5km");
    }

    @Test
    @DisplayName("create com paradas: persiste stops e roteia por todos os waypoints")
    void createWithStopsPersistsStopsAndRoutesThroughThem() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("8.000"), 20, "poly_8km"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(20), any(RideCategory.class), anyString()))
                .thenReturn(new BigDecimal("30.00"));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        List<StopPoint> stops = List.of(
                new StopPoint(new BigDecimal("-20.82500"), new BigDecimal("-49.38500")),
                new StopPoint(new BigDecimal("-20.82800"), new BigDecimal("-49.38800")));
        CreateRideRequest req = new CreateRideRequest(
                new BigDecimal("-20.82000"), new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"), new BigDecimal("-49.39000"),
                RideCategory.CARRO, stops);

        RideResponse resp = rideService.create(pax, req);

        assertThat(resp.stops()).hasSize(2);
        assertThat(resp.preco()).isEqualByComparingTo("30.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeoPoint>> waypoints = ArgumentCaptor.forClass(List.class);
        verify(mapsService).route(waypoints.capture());
        // origem + 2 paradas + destino
        assertThat(waypoints.getValue()).hasSize(4);

        ArgumentCaptor<Ride> saved = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(saved.capture());
        assertThat(saved.getValue().getStops()).hasSize(2);
    }

    @Test
    @DisplayName("create: persiste endereco textual de origem/destino (trim, vazio vira null)")
    void createPersistsTrimmedAddresses() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("5.000"), 12, "poly_addr"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(12), any(RideCategory.class), anyString()))
                .thenReturn(new BigDecimal("18.40"));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        CreateRideRequest req = new CreateRideRequest(
                new BigDecimal("-20.82000"), new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"), new BigDecimal("-49.39000"),
                RideCategory.CARRO, "  Rua A, 100  ", "   ", null);

        rideService.create(pax, req);

        ArgumentCaptor<Ride> saved = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(saved.capture());
        assertThat(saved.getValue().getOrigemEndereco()).isEqualTo("Rua A, 100");
        assertThat(saved.getValue().getDestinoEndereco()).isNull(); // blank → null
    }

    // ------------------------------------------------------------------------
    // recentDestinations()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("recentDestinations: delega ao repository e mapeia as linhas")
    void recentDestinationsMapsRows() {
        Instant when = Instant.now();
        when(rideRepository.findRecentDestinations(eq(pax.userId()), eq(5)))
                .thenReturn(List.of(row("Shopping", "-20.81", "-49.37", when)));

        List<RecentDestinationResponse> out = rideService.recentDestinations(pax, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).address()).isEqualTo("Shopping");
        assertThat(out.get(0).lat()).isEqualByComparingTo("-20.81");
        assertThat(out.get(0).lastUsedAt()).isEqualTo(when);
    }

    @Test
    @DisplayName("recentDestinations: saneia o limite para [1, 20]")
    void recentDestinationsClampsLimit() {
        when(rideRepository.findRecentDestinations(eq(pax.userId()), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        rideService.recentDestinations(pax, 999);
        rideService.recentDestinations(pax, 0);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(rideRepository, org.mockito.Mockito.times(2))
                .findRecentDestinations(eq(pax.userId()), limit.capture());
        assertThat(limit.getAllValues()).containsExactly(20, 1);
    }

    // ------------------------------------------------------------------------
    // estimate()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("estimate: usa OSRM + PricingPolicy e NÃO persiste ride")
    void estimateReturnsPriceWithoutPersisting() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("3.500"), 9, "poly_estimate"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(9), any(RideCategory.class), anyString()))
                .thenReturn(new BigDecimal("14.65"));

        EstimateResponse resp = rideService.estimate(pax, new EstimateRequest(
                new BigDecimal("-20.82000"),
                new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"),
                new BigDecimal("-49.39000")
        ));

        assertThat(resp.distanciaKm()).isEqualByComparingTo("3.500");
        assertThat(resp.tempoMin()).isEqualTo(9);
        assertThat(resp.preco()).isEqualByComparingTo("14.65");
        assertThat(resp.geometry()).isEqualTo("poly_estimate");
        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    @DisplayName("estimate: uma rota, precos por categoria em options (escolha sua corrida)")
    void estimateReturnsAllCategoryOptions() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("4.000"), 10, "poly_multi"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(10), eq(RideCategory.MOTO), anyString()))
                .thenReturn(new BigDecimal("9.50"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(10), eq(RideCategory.CARRO), anyString()))
                .thenReturn(new BigDecimal("16.30"));

        EstimateResponse resp = rideService.estimate(pax, new EstimateRequest(
                new BigDecimal("-20.82000"), new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"), new BigDecimal("-49.39000")));

        // mesma rota para todas as opcoes; OSRM chamado uma unica vez
        verify(mapsService).route(anyList());
        assertThat(resp.options())
                .extracting(CategoryOption::category, CategoryOption::preco)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(RideCategory.MOTO, new BigDecimal("9.50")),
                        org.assertj.core.groups.Tuple.tuple(RideCategory.CARRO, new BigDecimal("16.30")));
        // preco de compatibilidade = categoria default (CARRO)
        assertThat(resp.preco()).isEqualByComparingTo("16.30");
    }

    // ------------------------------------------------------------------------
    // surge pricing
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("create: surge multiplica a tarifa base e e congelado na ride")
    void createAppliesAndFreezesSurge() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("5.000"), 12, "poly_surge"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(12), any(RideCategory.class), anyString()))
                .thenReturn(new BigDecimal("20.00"));
        when(surgePolicy.multiplier(eq(CIDADE), eq(RideCategory.CARRO)))
                .thenReturn(new BigDecimal("1.30"));
        when(rideRepository.save(any(Ride.class))).thenAnswer(inv -> {
            Ride r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        RideResponse resp = rideService.create(pax, defaultRequest());

        // 20.00 * 1.30 = 26.00, travado no preco e no multiplicador da ride
        assertThat(resp.preco()).isEqualByComparingTo("26.00");
        assertThat(resp.surgeMultiplier()).isEqualByComparingTo("1.30");
        ArgumentCaptor<Ride> saved = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(saved.capture());
        assertThat(saved.getValue().getSurgeMultiplier()).isEqualByComparingTo("1.30");
    }

    @Test
    @DisplayName("estimate: surge por categoria entra no preco e e exposto no multiplicador")
    void estimateAppliesSurgePerCategory() {
        when(mapsService.route(anyList()))
                .thenReturn(new RouteInfo(new BigDecimal("4.000"), 10, "poly_surge_est"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(10), eq(RideCategory.MOTO), anyString()))
                .thenReturn(new BigDecimal("10.00"));
        when(pricingPolicy.calculate(any(BigDecimal.class), eq(10), eq(RideCategory.CARRO), anyString()))
                .thenReturn(new BigDecimal("20.00"));
        when(surgePolicy.multiplier(eq(CIDADE), eq(RideCategory.MOTO))).thenReturn(new BigDecimal("1.20"));
        when(surgePolicy.multiplier(eq(CIDADE), eq(RideCategory.CARRO))).thenReturn(new BigDecimal("1.50"));

        EstimateResponse resp = rideService.estimate(pax, new EstimateRequest(
                new BigDecimal("-20.82000"), new BigDecimal("-49.38000"),
                new BigDecimal("-20.83000"), new BigDecimal("-49.39000")));

        assertThat(resp.options())
                .extracting(CategoryOption::category, CategoryOption::preco, CategoryOption::surgeMultiplier)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(RideCategory.MOTO, new BigDecimal("12.00"), new BigDecimal("1.20")),
                        org.assertj.core.groups.Tuple.tuple(RideCategory.CARRO, new BigDecimal("30.00"), new BigDecimal("1.50")));
        // compatibilidade: categoria default (CARRO) com surge 1.50 -> 30.00
        assertThat(resp.preco()).isEqualByComparingTo("30.00");
        assertThat(resp.surgeMultiplier()).isEqualByComparingTo("1.50");
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
    @DisplayName("listMural delega para o repository com a cidade + categoria do motorista")
    void listMuralCallsAuthThenRepositoryWithMotoristaCidade() {
        when(rideRepository.findMural(CIDADE, RideCategory.CARRO)).thenReturn(java.util.List.of());

        rideService.listMural(mot);

        verify(driverService).assertCanAcceptRides(mot.userId());
        verify(rideRepository).findMural(CIDADE, RideCategory.CARRO);
    }

    @Test
    @DisplayName("accept: transição AVAILABLE_IN_MURAL → DRIVER_EN_ROUTE + acceptedAt")
    void acceptTransitionsAndStampsAcceptedAt() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));

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
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.accept(mot, ride.getId()))
                .isInstanceOf(DriverCityMismatchException.class);
    }

    @Test
    @DisplayName("accept fora de AVAILABLE_IN_MURAL → IllegalRideTransitionException")
    void acceptFailsIfNotAvailable() {
        Ride ride = ridePending(pax.userId());
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.accept(mot, ride.getId()))
                .isInstanceOf(IllegalRideTransitionException.class);
    }

    @Test
    @DisplayName("accept com localizacao: calcula ETA via OSRM e semeia posicao do motorista")
    void acceptWithLocationComputesPickupEta() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));
        when(mapsService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteInfo(new BigDecimal("2.100"), 7, "poly_pickup"));

        AcceptRideRequest body = new AcceptRideRequest(
                new BigDecimal("-20.81000"), new BigDecimal("-49.37000"));
        RideResponse resp = rideService.accept(mot, ride.getId(), body);

        assertThat(resp.status()).isEqualTo(RideStatus.DRIVER_EN_ROUTE);
        assertThat(resp.pickupEtaMin()).isEqualTo(7);
        assertThat(resp.driverCurrentLat()).isEqualByComparingTo(body.lat());
        assertThat(resp.driverDistanceKm()).isNotNull();
    }

    @Test
    @DisplayName("accept com localizacao mas OSRM fora: aceita sem ETA (best-effort)")
    void acceptWithLocationStillSucceedsWhenOsrmFails() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));
        when(mapsService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new MapsUnavailableException("OSRM fora"));

        RideResponse resp = rideService.accept(mot, ride.getId(), new AcceptRideRequest(
                new BigDecimal("-20.81000"), new BigDecimal("-49.37000")));

        assertThat(resp.status()).isEqualTo(RideStatus.DRIVER_EN_ROUTE);
        assertThat(resp.pickupEtaMin()).isNull();
        assertThat(resp.driverCurrentLat()).isNotNull();
    }

    @Test
    @DisplayName("accept: persiste via save() do detached (preserva o lock otimista @Version)")
    void acceptPersistsViaExplicitSave() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));

        rideService.accept(mot, ride.getId());

        // O accept roda fora de transacao: a mutacao so chega ao banco por um
        // save() explicito. E nesse UPDATE ... WHERE version=? que o @Version
        // barra dois motoristas aceitando a mesma corrida (→ 409).
        ArgumentCaptor<Ride> saved = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(ride.getId());
        assertThat(saved.getValue().getStatus()).isEqualTo(RideStatus.DRIVER_EN_ROUTE);
        assertThat(saved.getValue().getMotoristaId()).isEqualTo(mot.userId());
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
    // expireRide() — expiracao no mural
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("expireRide: AVAILABLE_IN_MURAL → EXPIRED + expiredAt")
    void expireRideTransitionsAvailableToExpired() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        boolean expired = rideService.expireRide(ride.getId());

        assertThat(expired).isTrue();
        assertThat(ride.getStatus()).isEqualTo(RideStatus.EXPIRED);
        assertThat(ride.getExpiredAt()).isNotNull();
    }

    @Test
    @DisplayName("expireRide: corrida ja aceita não é expirada (retorna false)")
    void expireRideSkipsRideAlreadyAccepted() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        boolean expired = rideService.expireRide(ride.getId());

        assertThat(expired).isFalse();
        assertThat(ride.getStatus()).isEqualTo(RideStatus.DRIVER_EN_ROUTE);
    }

    @Test
    @DisplayName("expireRide: corrida inexistente retorna false sem quebrar")
    void expireRideReturnsFalseWhenRideGone() {
        UUID id = UUID.randomUUID();
        when(rideRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(rideService.expireRide(id)).isFalse();
    }

    @Test
    @DisplayName("findExpirableRideIds delega para o repository com o cutoff")
    void findExpirableRideIdsDelegatesToRepository() {
        Instant cutoff = Instant.now();
        UUID id = UUID.randomUUID();
        when(rideRepository.findExpirableRideIds(cutoff)).thenReturn(java.util.List.of(id));

        assertThat(rideService.findExpirableRideIds(cutoff)).containsExactly(id);
        verify(rideRepository).findExpirableRideIds(cutoff);
    }

    // ------------------------------------------------------------------------
    // SSE de status (regra 18)
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("accept: emite evento de status DRIVER_EN_ROUTE sem fechar o stream")
    void acceptBroadcastsStatusEvent() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findByIdFetchingStops(ride.getId())).thenReturn(Optional.of(ride));

        rideService.accept(mot, ride.getId());

        ArgumentCaptor<RideStatusEvent> ev = ArgumentCaptor.forClass(RideStatusEvent.class);
        verify(statusSseHub).broadcast(eq(ride.getId()), ev.capture());
        assertThat(ev.getValue().status()).isEqualTo(RideStatus.DRIVER_EN_ROUTE);
        assertThat(ev.getValue().terminal()).isFalse();
        verify(statusSseHub, never()).closeRide(any());
    }

    @Test
    @DisplayName("complete: emite status COMPLETED (terminal) e fecha o stream de status")
    void completeBroadcastsTerminalAndCloses() {
        Ride ride = rideInProgress(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        rideService.complete(mot, ride.getId());

        ArgumentCaptor<RideStatusEvent> ev = ArgumentCaptor.forClass(RideStatusEvent.class);
        verify(statusSseHub).broadcast(eq(ride.getId()), ev.capture());
        assertThat(ev.getValue().status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(ev.getValue().terminal()).isTrue();
        verify(statusSseHub).closeRide(ride.getId());
    }

    @Test
    @DisplayName("cancel: emite status CANCELLED (terminal) e fecha o stream de status")
    void cancelBroadcastsTerminalAndCloses() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        rideService.cancel(pax, ride.getId(), null);

        ArgumentCaptor<RideStatusEvent> ev = ArgumentCaptor.forClass(RideStatusEvent.class);
        verify(statusSseHub).broadcast(eq(ride.getId()), ev.capture());
        assertThat(ev.getValue().status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(ev.getValue().cancelledBy()).isEqualTo(CancelledBy.PASSAGEIRO);
        verify(statusSseHub).closeRide(ride.getId());
    }

    @Test
    @DisplayName("expireRide: emite status EXPIRED (terminal) e fecha o stream de status")
    void expireRideBroadcastsTerminal() {
        Ride ride = rideAvailable(pax.userId(), CIDADE);
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        rideService.expireRide(ride.getId());

        ArgumentCaptor<RideStatusEvent> ev = ArgumentCaptor.forClass(RideStatusEvent.class);
        verify(statusSseHub).broadcast(eq(ride.getId()), ev.capture());
        assertThat(ev.getValue().status()).isEqualTo(RideStatus.EXPIRED);
        verify(statusSseHub).closeRide(ride.getId());
    }

    @Test
    @DisplayName("subscribeStatus: participante registra emitter (sem fechar em estado ativo)")
    void subscribeStatusRegistersForParticipant() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(statusSseHub.register(ride.getId())).thenReturn(new SseEmitter());

        SseEmitter emitter = rideService.subscribeStatus(pax, ride.getId());

        assertThat(emitter).isNotNull();
        verify(statusSseHub).register(ride.getId());
        verify(statusSseHub, never()).closeRide(any());
    }

    @Test
    @DisplayName("subscribeStatus: corrida em estado final manda snapshot e fecha")
    void subscribeStatusClosesWhenTerminal() {
        Ride ride = rideCompleted(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(statusSseHub.register(ride.getId())).thenReturn(new SseEmitter());

        rideService.subscribeStatus(pax, ride.getId());

        verify(statusSseHub).closeRide(ride.getId());
    }

    @Test
    @DisplayName("subscribeStatus: não-participante → RideAccessDeniedException, sem registrar")
    void subscribeStatusDeniedForIntruder() {
        Ride ride = rideEnRoute(pax.userId(), mot.userId());
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.subscribeStatus(outroPax, ride.getId()))
                .isInstanceOf(RideAccessDeniedException.class);
        verify(statusSseHub, never()).register(any());
    }

    // ------------------------------------------------------------------------
    // Builders auxiliares
    // ------------------------------------------------------------------------

    private static RideRepository.RecentDestinationRow row(String address, String lat, String lng, Instant when) {
        return new RideRepository.RecentDestinationRow() {
            public String getAddress() { return address; }
            public BigDecimal getLat() { return new BigDecimal(lat); }
            public BigDecimal getLng() { return new BigDecimal(lng); }
            public Instant getLastUsedAt() { return when; }
        };
    }

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
        r.setCategory(RideCategory.CARRO);
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
