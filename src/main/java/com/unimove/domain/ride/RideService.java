package com.unimove.domain.ride;

import com.unimove.domain.chat.ChatSseHub;
import com.unimove.domain.maps.GeoPoint;
import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.dto.AdminRideItem;
import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.EarningsAggregate;
import com.unimove.domain.ride.dto.EarningsDayItem;
import com.unimove.domain.ride.dto.EarningsResponse;
import com.unimove.domain.ride.dto.EstimateRequest;
import com.unimove.domain.ride.dto.EstimateResponse;
import com.unimove.domain.ride.dto.RatingResponse;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
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

    private final RideRepository rideRepository;
    private final RideRatingRepository rideRatingRepository;
    private final MapsService mapsService;
    private final PricingPolicy pricingPolicy;
    private final CancellationPolicy cancellationPolicy;
    private final PaymentService paymentService;
    private final DriverService driverService;
    private final UserRatingService userRatingService;
    private final UserAccountService userAccountService;
    private final ChatSseHub chatSseHub;

    public RideService(RideRepository rideRepository,
                       RideRatingRepository rideRatingRepository,
                       MapsService mapsService,
                       PricingPolicy pricingPolicy,
                       CancellationPolicy cancellationPolicy,
                       PaymentService paymentService,
                       DriverService driverService,
                       UserRatingService userRatingService,
                       UserAccountService userAccountService,
                       ChatSseHub chatSseHub) {
        this.rideRepository = rideRepository;
        this.rideRatingRepository = rideRatingRepository;
        this.mapsService = mapsService;
        this.pricingPolicy = pricingPolicy;
        this.cancellationPolicy = cancellationPolicy;
        this.paymentService = paymentService;
        this.driverService = driverService;
        this.userRatingService = userRatingService;
        this.userAccountService = userAccountService;
        this.chatSseHub = chatSseHub;
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(AuthenticatedUser passageiro, EstimateRequest req) {
        RouteInfo route = mapsService.route(buildWaypoints(
                req.latOrigem(), req.lngOrigem(), req.latDestino(), req.lngDestino(), req.stops()));
        RideCategory category = req.category() != null ? req.category() : RideCategory.CARRO;
        BigDecimal preco = pricingPolicy.calculate(
                route.distanciaKm(), route.tempoMin(), category, passageiro.cidade());
        return new EstimateResponse(route.distanciaKm(), route.tempoMin(), preco);
    }

    @Transactional
    public RideResponse create(AuthenticatedUser passageiro, CreateRideRequest req) {
        userAccountService.requireActive(passageiro.userId());
        RouteInfo route = mapsService.route(buildWaypoints(
                req.latOrigem(), req.lngOrigem(), req.latDestino(), req.lngDestino(), req.stops()));

        RideCategory category = req.category() != null ? req.category() : RideCategory.CARRO;
        BigDecimal preco = pricingPolicy.calculate(
                route.distanciaKm(), route.tempoMin(), category, passageiro.cidade());

        Ride ride = new Ride();
        ride.setPassageiroId(passageiro.userId());
        ride.setCidade(passageiro.cidade());
        ride.setCategory(category);
        ride.setLatOrigem(req.latOrigem());
        ride.setLngOrigem(req.lngOrigem());
        ride.setLatDestino(req.latDestino());
        ride.setLngDestino(req.lngDestino());
        if (req.stops() != null) {
            for (StopPoint s : req.stops()) {
                ride.getStops().add(new RideStop(s.lat(), s.lng()));
            }
        }
        ride.setDistanciaKm(route.distanciaKm());
        ride.setTempoMin(route.tempoMin());
        ride.setPreco(preco);
        ride.setStatus(RideStatus.PENDING_PAYMENT);

        Ride saved = rideRepository.save(ride);
        log.info("Ride {} criada por passageiro {} (cidade={}, category={}, paradas={}, preco={})",
                saved.getId(), passageiro.userId(), saved.getCidade(), category,
                saved.getStops().size(), preco);

        return RideResponse.from(saved);
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
        return RideResponse.from(ride);
    }

    @Transactional(readOnly = true)
    public List<RideMuralItem> listMural(AuthenticatedUser motorista) {
        driverService.assertCanAcceptRides(motorista.userId());
        VehicleType vt = driverService.getVehicleType(motorista.userId());
        return rideRepository.findMural(motorista.cidade(), RideCategory.fromVehicleType(vt));
    }

    @Transactional
    public RideResponse accept(AuthenticatedUser motorista, UUID rideId) {
        userAccountService.requireActive(motorista.userId());
        driverService.assertCanAcceptRides(motorista.userId());

        Ride ride = rideRepository.findById(rideId)
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
        ride.setAcceptedAt(Instant.now());

        log.info("Ride {} aceita pelo motorista {}", ride.getId(), motorista.userId());
        return RideResponse.from(ride);
    }

    @Transactional
    public RideResponse start(AuthenticatedUser motorista, UUID rideId) {
        Ride ride = loadAsAcceptingDriver(motorista, rideId);
        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.IN_PROGRESS);
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        log.info("Ride {} iniciada pelo motorista {}", ride.getId(), motorista.userId());
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

        boolean isPassenger = user.role() == Role.PASSAGEIRO
                && ride.getPassageiroId().equals(user.userId());
        boolean isAcceptingDriver = user.role() == Role.MOTORISTA
                && user.userId().equals(ride.getMotoristaId());

        if (!isPassenger && !isAcceptingDriver) {
            throw new RideAccessDeniedException();
        }

        BigDecimal motoristaAvg = null;
        Integer motoristaCount = null;
        if (ride.getMotoristaId() != null) {
            RatingStats stats = userRatingService.getStats(ride.getMotoristaId());
            motoristaAvg = stats.avg();
            motoristaCount = stats.count();
        }

        return RideResponse.from(ride, computeDriverDistanceKm(ride), motoristaAvg, motoristaCount);
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
