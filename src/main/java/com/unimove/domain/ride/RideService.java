package com.unimove.domain.ride;

import com.unimove.domain.chat.ChatSseHub;
import com.unimove.domain.maps.GeoPoint;
import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.dto.AcceptRideRequest;
import com.unimove.domain.ride.dto.AdminRideItem;
import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.CategoryOption;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.EarningsAggregate;
import com.unimove.domain.ride.dto.EarningsDayItem;
import com.unimove.domain.ride.dto.EarningsResponse;
import com.unimove.domain.ride.dto.EstimateRequest;
import com.unimove.domain.ride.dto.EstimateResponse;
import com.unimove.domain.ride.dto.RatingResponse;
import com.unimove.domain.ride.dto.RecentDestinationResponse;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.ride.dto.RideRouteResponse;
import com.unimove.domain.ride.dto.RideStatusEvent;
import com.unimove.domain.ride.dto.StopPoint;
import com.unimove.domain.ride.dto.SubmitRatingRequest;
import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.RatingStats;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.UserAccountService;
import com.unimove.domain.user.UserRatingService;
import com.unimove.domain.user.VehicleType;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.util.Haversine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    /** Teto de itens na lista de destinos recentes (tela "para onde vamos?"). */
    private static final int MAX_RECENT_DESTINATIONS = 20;

    /** Estados em que a corrida ainda esta "viva" para o passageiro — bloqueiam criar outra. */
    private static final List<RideStatus> PASSENGER_ACTIVE_STATUSES = List.of(
            RideStatus.PENDING_PAYMENT, RideStatus.AVAILABLE_IN_MURAL,
            RideStatus.DRIVER_EN_ROUTE, RideStatus.IN_PROGRESS);

    /** Estados em que o motorista esta comprometido com uma corrida — bloqueiam novo aceite. */
    private static final List<RideStatus> DRIVER_ACTIVE_STATUSES = List.of(
            RideStatus.DRIVER_EN_ROUTE, RideStatus.IN_PROGRESS);

    private final RideRepository rideRepository;
    private final RideRatingRepository rideRatingRepository;
    private final MapsService mapsService;
    private final PricingPolicy pricingPolicy;
    private final SurgePolicy surgePolicy;
    private final CancellationPolicy cancellationPolicy;
    private final PaymentService paymentService;
    private final DriverService driverService;
    private final UserRatingService userRatingService;
    private final UserAccountService userAccountService;
    private final ChatSseHub chatSseHub;
    private final RideStatusSseHub statusSseHub;
    private final MuralSseHub muralSseHub;

    public RideService(RideRepository rideRepository,
                       RideRatingRepository rideRatingRepository,
                       MapsService mapsService,
                       PricingPolicy pricingPolicy,
                       SurgePolicy surgePolicy,
                       CancellationPolicy cancellationPolicy,
                       PaymentService paymentService,
                       DriverService driverService,
                       UserRatingService userRatingService,
                       UserAccountService userAccountService,
                       ChatSseHub chatSseHub,
                       RideStatusSseHub statusSseHub,
                       MuralSseHub muralSseHub) {
        this.rideRepository = rideRepository;
        this.rideRatingRepository = rideRatingRepository;
        this.mapsService = mapsService;
        this.pricingPolicy = pricingPolicy;
        this.surgePolicy = surgePolicy;
        this.cancellationPolicy = cancellationPolicy;
        this.paymentService = paymentService;
        this.driverService = driverService;
        this.userRatingService = userRatingService;
        this.userAccountService = userAccountService;
        this.chatSseHub = chatSseHub;
        this.statusSseHub = statusSseHub;
        this.muralSseHub = muralSseHub;
    }

    // Sem @Transactional de proposito: estimate so toca o OSRM (chamada externa)
    // e o PricingPolicy (cache em memoria). Abrir uma transacao aqui prenderia
    // uma conexao do pool durante todo o round-trip ao OSRM, sem nenhum acesso
    // ao banco para justificar (regra 10).
    public EstimateResponse estimate(AuthenticatedUser passageiro, EstimateRequest req) {
        RouteInfo route = mapsService.route(buildWaypoints(
                req.latOrigem(), req.lngOrigem(), req.latDestino(), req.lngDestino(), req.stops()));

        // Mesma rota (uma chamada ao OSRM) precificada em cada categoria — tela
        // "escolha sua corrida". So o preco varia entre as opcoes. O surge e por
        // categoria (oferta/demanda independentes) e ja entra no preco exibido.
        RideCategory category = req.category() != null ? req.category() : RideCategory.CARRO;
        List<CategoryOption> options = new ArrayList<>();
        BigDecimal preco = null;
        BigDecimal surge = BigDecimal.ONE.setScale(2);
        for (RideCategory c : RideCategory.values()) {
            BigDecimal mult = surgePolicy.multiplier(passageiro.cidade(), c);
            BigDecimal p = applySurge(pricingPolicy.calculate(
                    route.distanciaKm(), route.tempoMin(), c, passageiro.cidade()), mult);
            options.add(new CategoryOption(c, p, mult));
            // preco/surge mantidos por compatibilidade: categoria pedida (ou CARRO default).
            if (c == category) {
                preco = p;
                surge = mult;
            }
        }

        return new EstimateResponse(route.distanciaKm(), route.tempoMin(), preco, surge, route.geometry(), options);
    }

    // Sem @Transactional no metodo: a rota do OSRM (chamada externa, potencialmente
    // lenta) e calculada ANTES de tocar o banco, fora de qualquer transacao. O
    // requireActive e o save() rodam cada um em sua transacao curta — assim nenhuma
    // conexao do pool fica presa durante o round-trip ao OSRM (regra 10).
    public RideResponse create(AuthenticatedUser passageiro, CreateRideRequest req) {
        userAccountService.requireActive(passageiro.userId());
        // Uma corrida ativa por passageiro. Checagem barata no banco ANTES do
        // round-trip ao OSRM. Como o metodo roda sem transacao, ha uma janela
        // de corrida entre o exists e o save — aceitavel no MVP.
        if (rideRepository.existsByPassageiroIdAndStatusIn(passageiro.userId(), PASSENGER_ACTIVE_STATUSES)) {
            throw ActiveRideExistsException.forPassenger();
        }
        RouteInfo route = mapsService.route(buildWaypoints(
                req.latOrigem(), req.lngOrigem(), req.latDestino(), req.lngDestino(), req.stops()));

        RideCategory category = req.category() != null ? req.category() : RideCategory.CARRO;
        // Surge resolvido e CONGELADO aqui: o passageiro paga este multiplicador,
        // gravado junto com o preco — sem race condition (preco fixado no create).
        BigDecimal surge = surgePolicy.multiplier(passageiro.cidade(), category);
        BigDecimal preco = applySurge(pricingPolicy.calculate(
                route.distanciaKm(), route.tempoMin(), category, passageiro.cidade()), surge);

        Ride ride = new Ride();
        ride.setPassageiroId(passageiro.userId());
        ride.setCidade(passageiro.cidade());
        ride.setCategory(category);
        ride.setLatOrigem(req.latOrigem());
        ride.setLngOrigem(req.lngOrigem());
        ride.setLatDestino(req.latDestino());
        ride.setLngDestino(req.lngDestino());
        ride.setOrigemEndereco(trimToNull(req.origemEndereco()));
        ride.setDestinoEndereco(trimToNull(req.destinoEndereco()));
        if (req.stops() != null) {
            for (StopPoint s : req.stops()) {
                ride.getStops().add(new RideStop(s.lat(), s.lng()));
            }
        }
        ride.setDistanciaKm(route.distanciaKm());
        ride.setTempoMin(route.tempoMin());
        ride.setRouteGeometry(route.geometry());
        ride.setPreco(preco);
        ride.setSurgeMultiplier(surge);
        ride.setStatus(RideStatus.PENDING_PAYMENT);

        Ride saved = rideRepository.save(ride);
        log.info("Ride {} criada por passageiro {} (cidade={}, category={}, paradas={}, preco={})",
                saved.getId(), passageiro.userId(), saved.getCidade(), category,
                saved.getStops().size(), preco);

        return RideResponse.from(saved);
    }

    /** Aplica o multiplicador de surge sobre a tarifa base. Compartilhado por estimate e create. */
    private static BigDecimal applySurge(BigDecimal base, BigDecimal multiplier) {
        return base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Monta a sequencia ordenada de waypoints para o OSRM: origem, paradas intermediarias
     * (na ordem recebida), destino. Distancia/tempo da rota total ja incluem o desvio.
     */
    private List<GeoPoint> buildWaypoints(BigDecimal latOrigem, BigDecimal lngOrigem,
                                          BigDecimal latDestino, BigDecimal lngDestino,
                                          List<StopPoint> stops) {
        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(new GeoPoint(latOrigem.doubleValue(), lngOrigem.doubleValue()));
        if (stops != null) {
            for (StopPoint s : stops) {
                waypoints.add(new GeoPoint(s.lat().doubleValue(), s.lng().doubleValue()));
            }
        }
        waypoints.add(new GeoPoint(latDestino.doubleValue(), lngDestino.doubleValue()));
        return waypoints;
    }

    @Transactional
    public RideResponse confirmPayment(AuthenticatedUser passageiro,
                                       UUID rideId,
                                       ConfirmPaymentRequest req) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        if (!ride.getPassageiroId().equals(passageiro.userId())) {
            throw new RideAccessDeniedException();
        }

        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.AVAILABLE_IN_MURAL);

        ride.setPaymentMethod(req.method());
        if (req.method() == PaymentMethod.PIX) {
            ride.setPixPayload(paymentService.generatePixPayload(ride.getId(), ride.getPreco()));
        }
        ride.setStatus(RideStatus.AVAILABLE_IN_MURAL);

        log.info("Ride {} confirmada ({}) → AVAILABLE_IN_MURAL", ride.getId(), req.method());
        publishStatus(ride);
        publishMuralAdded(ride);
        return RideResponse.from(ride);
    }

    @Transactional(readOnly = true)
    public List<RideMuralItem> listMural(AuthenticatedUser motorista) {
        driverService.assertCanAcceptRides(motorista.userId());
        VehicleType vt = driverService.getVehicleType(motorista.userId());
        return rideRepository.findMural(motorista.cidade(), RideCategory.fromVehicleType(vt));
    }

    /** Aceite sem compartilhar localizacao — ETA de chegada na origem fica nulo. */
    public RideResponse accept(AuthenticatedUser motorista, UUID rideId) {
        return accept(motorista, rideId, null);
    }

    // Sem @Transactional no metodo: o ETA de chegada (chamada ao OSRM) e calculado
    // FORA de transacao para nao segurar conexao/lock do banco durante o round-trip
    // externo (regra 10). A entidade e carregada com as paradas ja inicializadas,
    // mutada em memoria e persistida por um save() em transacao curta — o merge do
    // detached mantem o lock otimista @Version (vide nota antes do save).
    public RideResponse accept(AuthenticatedUser motorista, UUID rideId, AcceptRideRequest req) {
        userAccountService.requireActive(motorista.userId());
        driverService.assertCanAcceptRides(motorista.userId());

        // Uma corrida ativa por motorista: quem esta DRIVER_EN_ROUTE/IN_PROGRESS
        // nao aceita outra ate finalizar.
        if (rideRepository.existsByMotoristaIdAndStatusIn(motorista.userId(), DRIVER_ACTIVE_STATUSES)) {
            throw ActiveRideExistsException.forDriver();
        }

        Ride ride = rideRepository.findByIdFetchingStops(rideId)
                .orElseThrow(RideNotFoundException::new);

        if (!ride.getCidade().equals(motorista.cidade())) {
            throw new DriverCityMismatchException();
        }

        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.DRIVER_EN_ROUTE);

        VehicleType vt = driverService.getVehicleType(motorista.userId());
        RideCategory driverCategory = RideCategory.fromVehicleType(vt);
        if (driverCategory != ride.getCategory()) {
            throw new CategoryMismatchException(ride.getCategory(), driverCategory);
        }

        ride.setMotoristaId(motorista.userId());
        ride.setStatus(RideStatus.DRIVER_EN_ROUTE);
        Instant now = Instant.now();
        ride.setAcceptedAt(now);

        // Se o app mandou a posicao do motorista, semeia a localizacao (o
        // passageiro ve posicao + distancia logo no aceite) e calcula UMA vez o
        // ETA de chegada na origem via OSRM. Best-effort: falha de mapa nao pode
        // impedir o aceite — regra 21 / regra 10 (OSRM nunca no polling).
        if (req != null) {
            ride.setDriverCurrentLat(req.lat());
            ride.setDriverCurrentLng(req.lng());
            ride.setDriverLocationUpdatedAt(now);
            computePickupEtaMin(req.lat(), req.lng(), ride).ifPresent(eta -> {
                ride.setPickupEtaMin(eta);
                ride.setPickupEtaAt(now);
            });
        }

        // Persiste o aceite numa transacao curta (merge do detached). O lock
        // otimista @Version vale no UPDATE ... WHERE version=?: se outro motorista
        // aceitou primeiro, o save() lanca ObjectOptimisticLockingFailureException
        // (→ 409) e o publishStatus abaixo nem chega a rodar.
        rideRepository.save(ride);

        log.info("Ride {} aceita pelo motorista {} (pickupEtaMin={})",
                ride.getId(), motorista.userId(), ride.getPickupEtaMin());
        publishStatus(ride);
        publishMuralRemoved(ride, MuralSseHub.REASON_ACCEPTED);
        return RideResponse.from(ride, computeDriverDistanceKm(ride));
    }

    /**
     * ETA de carro (minutos) do motorista ate a origem, UMA chamada ao OSRM no
     * aceite. Best-effort: se o gateway de mapas falhar, retorna vazio e o aceite
     * segue sem ETA (o passageiro cai no fallback de distancia haversine).
     */
    private java.util.Optional<Integer> computePickupEtaMin(BigDecimal driverLat, BigDecimal driverLng, Ride ride) {
        try {
            RouteInfo toPickup = mapsService.route(
                    driverLat.doubleValue(), driverLng.doubleValue(),
                    ride.getLatOrigem().doubleValue(), ride.getLngOrigem().doubleValue());
            return java.util.Optional.of(toPickup.tempoMin());
        } catch (RuntimeException e) {
            log.warn("Falha ao calcular ETA de chegada da ride {} (segue sem ETA): {}",
                    ride.getId(), e.toString());
            return java.util.Optional.empty();
        }
    }

    @Transactional
    public RideResponse start(AuthenticatedUser motorista, UUID rideId) {
        Ride ride = loadAsAcceptingDriver(motorista, rideId);
        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.IN_PROGRESS);
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        log.info("Ride {} iniciada pelo motorista {}", ride.getId(), motorista.userId());
        publishStatus(ride);
        return RideResponse.from(ride);
    }

    @Transactional
    public RideResponse complete(AuthenticatedUser motorista, UUID rideId) {
        Ride ride = loadAsAcceptingDriver(motorista, rideId);
        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.COMPLETED);
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(Instant.now());
        log.info("Ride {} finalizada pelo motorista {}", ride.getId(), motorista.userId());
        chatSseHub.closeRide(rideId);
        publishStatus(ride);
        return RideResponse.from(ride);
    }

    @Transactional
    public RideResponse cancel(AuthenticatedUser user, UUID rideId, CancelRideRequest req) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        String reason = req == null ? null : req.reason();
        RideStatus statusBefore = ride.getStatus();

        if (user.role() == Role.PASSAGEIRO) {
            if (!ride.getPassageiroId().equals(user.userId())) {
                throw new RideAccessDeniedException();
            }
            ride.setCancelledBy(CancelledBy.PASSAGEIRO);
        } else if (user.role() == Role.MOTORISTA) {
            if (!user.userId().equals(ride.getMotoristaId())) {
                throw new RideAccessDeniedException();
            }
            if (ride.getStatus() != RideStatus.DRIVER_EN_ROUTE) {
                throw new IllegalRideTransitionException(ride.getStatus(), RideStatus.CANCELLED);
            }
            if (reason == null || reason.isBlank()) {
                throw new MissingCancelReasonException();
            }
            ride.setCancelledBy(CancelledBy.MOTORISTA);
        } else {
            throw new RideAccessDeniedException();
        }

        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.CANCELLED);
        Instant now = Instant.now();
        BigDecimal fee = cancellationPolicy.computeFee(
                user.role(), statusBefore, ride.getAcceptedAt(), now);
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(now);
        ride.setCancelReason(reason);
        ride.setCancellationFee(fee.signum() > 0 ? fee : null);

        log.info("Ride {} cancelada por {} (role={}, fee={})",
                ride.getId(), user.userId(), user.role(), ride.getCancellationFee());
        chatSseHub.closeRide(rideId);
        publishStatus(ride);
        if (statusBefore == RideStatus.AVAILABLE_IN_MURAL) {
            publishMuralRemoved(ride, MuralSseHub.REASON_CANCELLED);
        }
        return RideResponse.from(ride);
    }

    @Transactional
    public RideResponse updateDriverLocation(AuthenticatedUser motorista,
                                             UUID rideId,
                                             UpdateDriverLocationRequest req) {
        Ride ride = loadAsAcceptingDriver(motorista, rideId);

        RideStatus s = ride.getStatus();
        if (s != RideStatus.DRIVER_EN_ROUTE && s != RideStatus.IN_PROGRESS) {
            throw new LocationUpdateNotAllowedException(s);
        }

        ride.setDriverCurrentLat(req.lat());
        ride.setDriverCurrentLng(req.lng());
        ride.setDriverLocationUpdatedAt(Instant.now());

        return RideResponse.from(ride, computeDriverDistanceKm(ride));
    }

    /**
     * Valida que o usuario participa da corrida e que o estado permite chat
     * (DRIVER_EN_ROUTE ou IN_PROGRESS). Usado pelo ChatService.
     * Retorna a Role do remetente para nao precisar recarregar do banco.
     */
    @Transactional(readOnly = true)
    public Role assertChatAllowed(AuthenticatedUser user, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        RideStatus s = ride.getStatus();
        if (s != RideStatus.DRIVER_EN_ROUTE && s != RideStatus.IN_PROGRESS) {
            throw new com.unimove.domain.chat.ChatNotAllowedException(
                    "Chat indisponível no estado atual da corrida (" + s + ").");
        }

        boolean isPassenger = user.role() == Role.PASSAGEIRO
                && ride.getPassageiroId().equals(user.userId());
        boolean isAcceptingDriver = user.role() == Role.MOTORISTA
                && user.userId().equals(ride.getMotoristaId());
        if (!isPassenger && !isAcceptingDriver) {
            throw new RideAccessDeniedException();
        }
        return user.role();
    }

    @Transactional(readOnly = true)
    public RideResponse get(AuthenticatedUser user, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        assertParticipant(user, ride);

        BigDecimal motoristaAvg = null;
        Integer motoristaCount = null;
        if (ride.getMotoristaId() != null) {
            RatingStats stats = userRatingService.getStats(ride.getMotoristaId());
            motoristaAvg = stats.avg();
            motoristaCount = stats.count();
        }

        return RideResponse.from(ride, computeDriverDistanceKm(ride), motoristaAvg, motoristaCount);
    }

    /**
     * Geometria (polyline) do trajeto da corrida (regra 19). Endpoint leve, chamado
     * UMA vez pelo front para desenhar a rota — fora do polling. A linha e estatica
     * durante a corrida, entao nao precisa entrar no GET /rides/{id}.
     */
    @Transactional(readOnly = true)
    public RideRouteResponse getRoute(AuthenticatedUser user, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);
        assertParticipant(user, ride);
        return new RideRouteResponse(ride.getRouteGeometry());
    }

    /**
     * Abre o SSE de status (regra 18). Emite imediatamente um snapshot do estado
     * atual — assim, mesmo reconectando depois de perder transicoes, o cliente
     * recebe a verdade corrente sem precisar de replay. Se a corrida ja esta em
     * estado final, manda o snapshot e fecha o stream em seguida.
     */
    @Transactional(readOnly = true)
    public SseEmitter subscribeStatus(AuthenticatedUser user, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);
        assertParticipant(user, ride);

        SseEmitter emitter = statusSseHub.register(rideId);
        RideStatusEvent snapshot = RideStatusEvent.from(ride);
        try {
            emitter.send(RideStatusSseHub.buildEvent(snapshot));
        } catch (IOException e) {
            emitter.complete();
            return emitter;
        }
        if (snapshot.terminal()) {
            statusSseHub.closeRide(rideId);
        }
        return emitter;
    }

    /**
     * Abre o SSE do mural (motorista online, mesma cidade/categoria). Emite
     * imediatamente um snapshot da lista atual — mesma garantia do status-stream:
     * reconectou, recebeu a verdade corrente. Depois, eventos incrementais
     * "ride-added"/"ride-removed" mantem a lista do app sem polling.
     */
    @Transactional(readOnly = true)
    public SseEmitter subscribeMural(AuthenticatedUser motorista) {
        driverService.assertCanAcceptRides(motorista.userId());
        RideCategory category = RideCategory.fromVehicleType(
                driverService.getVehicleType(motorista.userId()));
        List<RideMuralItem> snapshot = rideRepository.findMural(motorista.cidade(), category);

        SseEmitter emitter = muralSseHub.register(motorista.cidade(), category);
        try {
            emitter.send(MuralSseHub.snapshotEvent(snapshot));
        } catch (IOException e) {
            emitter.complete();
        }
        return emitter;
    }

    @Transactional(readOnly = true)
    public Page<AdminRideItem> listAdminRides(Pageable pageable) {
        return rideRepository.findAllForAdmin(pageable);
    }

    @Transactional
    public RatingResponse submitRating(AuthenticatedUser user, UUID rideId, SubmitRatingRequest req) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new RatingNotAllowedException(ride.getStatus());
        }

        UUID rateeId;
        if (user.role() == Role.PASSAGEIRO && ride.getPassageiroId().equals(user.userId())) {
            rateeId = ride.getMotoristaId();
        } else if (user.role() == Role.MOTORISTA && user.userId().equals(ride.getMotoristaId())) {
            rateeId = ride.getPassageiroId();
        } else {
            throw new RideAccessDeniedException();
        }

        if (rideRatingRepository.existsByRideIdAndRaterId(rideId, user.userId())) {
            throw new RatingAlreadySubmittedException();
        }

        RideRating rating = new RideRating();
        rating.setRideId(rideId);
        rating.setRaterId(user.userId());
        rating.setRateeId(rateeId);
        rating.setScore(req.score().shortValue());
        rating.setComment(req.comment());
        RideRating saved = rideRatingRepository.save(rating);

        RatingStats stats = userRatingService.applyRating(rateeId, req.score());

        log.info("Ride {} avaliada: rater={} ratee={} score={}", rideId, user.userId(), rateeId, req.score());

        return new RatingResponse(
                saved.getId(),
                rideId,
                user.userId(),
                rateeId,
                req.score(),
                req.comment(),
                saved.getCreatedAt(),
                stats.avg(),
                stats.count()
        );
    }

    @Transactional(readOnly = true)
    public Page<RideHistoryItem> history(AuthenticatedUser user, RideStatus status, Pageable pageable) {
        return switch (user.role()) {
            case PASSAGEIRO -> rideRepository.findHistoryForPassenger(user.userId(), status, pageable);
            case MOTORISTA -> rideRepository.findHistoryForDriver(user.userId(), status, pageable);
            default -> throw new RideAccessDeniedException();
        };
    }

    /**
     * Destinos recentes do passageiro para a tela "para onde vamos?" (estilo Uber).
     * Derivado do historico — endereco textual + coordenadas, dedup por coordenada
     * arredondada (regra 11) e ordenado por recencia. O limite e saneado em [1, MAX].
     */
    @Transactional(readOnly = true)
    public List<RecentDestinationResponse> recentDestinations(AuthenticatedUser passageiro, int limit) {
        int safe = Math.max(1, Math.min(limit, MAX_RECENT_DESTINATIONS));
        return rideRepository.findRecentDestinations(passageiro.userId(), safe).stream()
                .map(r -> new RecentDestinationResponse(
                        r.getAddress(), r.getLat(), r.getLng(), r.getLastUsedAt()))
                .toList();
    }

    /**
     * IDs de corridas elegiveis a expiracao (em AVAILABLE_IN_MURAL ha mais que o TTL).
     * Chamado pelo {@code RideExpirationScheduler}.
     */
    @Transactional(readOnly = true)
    public List<UUID> findExpirableRideIds(Instant cutoff) {
        return rideRepository.findExpirableRideIds(cutoff);
    }

    /**
     * Expira UMA corrida (AVAILABLE_IN_MURAL → EXPIRED) em sua propria transacao.
     * Re-checa o estado pois a corrida pode ter sido aceita/cancelada entre a
     * varredura e este load. Se um motorista aceitar concorrentemente, o lock
     * otimista (@Version) faz esta transacao falhar no commit — o scheduler trata.
     *
     * @return true se expirou; false se ja nao estava no mural (corrida do meio).
     */
    @Transactional
    public boolean expireRide(UUID rideId) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null || ride.getStatus() != RideStatus.AVAILABLE_IN_MURAL) {
            return false;
        }
        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.EXPIRED);
        ride.setStatus(RideStatus.EXPIRED);
        ride.setExpiredAt(Instant.now());
        log.info("Ride {} expirada no mural (nenhum motorista aceitou dentro do TTL)", rideId);
        publishStatus(ride);
        publishMuralRemoved(ride, MuralSseHub.REASON_EXPIRED);
        return true;
    }

    /** Passageiro dono ou motorista aceitante; qualquer outro → 403. */
    private void assertParticipant(AuthenticatedUser user, Ride ride) {
        boolean isPassenger = user.role() == Role.PASSAGEIRO
                && ride.getPassageiroId().equals(user.userId());
        boolean isAcceptingDriver = user.role() == Role.MOTORISTA
                && user.userId().equals(ride.getMotoristaId());
        if (!isPassenger && !isAcceptingDriver) {
            throw new RideAccessDeniedException();
        }
    }

    /**
     * Publica a transicao de estado no SSE de status (regra 18). Eventos
     * terminais fecham o stream apos o envio.
     */
    private void publishStatus(Ride ride) {
        RideStatusEvent event = RideStatusEvent.from(ride);
        UUID rideId = ride.getId();
        runAfterCommit(() -> {
            statusSseHub.broadcast(rideId, event);
            if (event.terminal()) {
                statusSseHub.closeRide(rideId);
            }
        });
    }

    /** Corrida entrou no mural — avisa os motoristas conectados ao stream da cidade/categoria. */
    private void publishMuralAdded(Ride ride) {
        RideMuralItem item = RideMuralItem.from(ride);
        String cidade = ride.getCidade();
        RideCategory category = ride.getCategory();
        runAfterCommit(() -> muralSseHub.broadcastAdded(cidade, category, item));
    }

    /** Corrida saiu do mural (aceita, cancelada ou expirada) — remove da lista dos conectados. */
    private void publishMuralRemoved(Ride ride, String reason) {
        UUID rideId = ride.getId();
        String cidade = ride.getCidade();
        RideCategory category = ride.getCategory();
        runAfterCommit(() -> muralSseHub.broadcastRemoved(cidade, category, rideId, reason));
    }

    /**
     * Broadcasts SSE rodam em afterCommit: so emitimos depois que a transacao
     * confirma, evitando notificar uma transicao que pode dar rollback — em
     * especial o expireRide, cujo commit pode falhar por lock otimista se um
     * motorista aceitar concorrentemente. Fora de transacao, roda na hora.
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Normaliza texto opcional: trim e converte vazio/blank em null. */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Ride loadAsAcceptingDriver(AuthenticatedUser motorista, UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);
        if (!motorista.userId().equals(ride.getMotoristaId())) {
            throw new RideAccessDeniedException();
        }
        return ride;
    }

    /**
     * DRIVER_EN_ROUTE: motorista → origem. IN_PROGRESS: motorista → destino.
     * Sem localização do motorista, retorna null.
     */
    private BigDecimal computeDriverDistanceKm(Ride ride) {
        if (ride.getDriverCurrentLat() == null || ride.getDriverCurrentLng() == null) {
            return null;
        }
        return switch (ride.getStatus()) {
            case DRIVER_EN_ROUTE -> Haversine.distanceKm(
                    ride.getDriverCurrentLat(), ride.getDriverCurrentLng(),
                    ride.getLatOrigem(), ride.getLngOrigem());
            case IN_PROGRESS -> Haversine.distanceKm(
                    ride.getDriverCurrentLat(), ride.getDriverCurrentLng(),
                    ride.getLatDestino(), ride.getLngDestino());
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public EarningsResponse getDriverEarnings(UUID driverId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveTo = (to != null) ? to : today;
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(29);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidEarningsRangeException();
        }

        Instant fromInstant = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstantExclusive = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        EarningsAggregate agg = rideRepository.sumDriverEarnings(driverId, fromInstant, toInstantExclusive);
        long total = agg == null ? 0L : agg.totalRides();
        BigDecimal gross = (agg == null || agg.grossEarnings() == null)
                ? BigDecimal.ZERO
                : agg.grossEarnings();

        BigDecimal average = total == 0
                ? BigDecimal.ZERO
                : gross.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        List<EarningsDayItem> byDay = rideRepository
                .findDriverEarningsByDay(driverId, fromInstant, toInstantExclusive).stream()
                .map(row -> new EarningsDayItem(
                        row.getDay().toLocalDate(),
                        row.getRides(),
                        row.getGross() == null ? BigDecimal.ZERO : row.getGross()
                ))
                .toList();

        return new EarningsResponse(effectiveFrom, effectiveTo, total, gross, average, byDay);
    }
}
