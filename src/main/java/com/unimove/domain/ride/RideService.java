package com.unimove.domain.ride;

import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.dto.AdminRideItem;
import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.util.Haversine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final MapsService mapsService;
    private final PricingPolicy pricingPolicy;
    private final PaymentService paymentService;
    private final DriverService driverService;

    public RideService(RideRepository rideRepository,
                       MapsService mapsService,
                       PricingPolicy pricingPolicy,
                       PaymentService paymentService,
                       DriverService driverService) {
        this.rideRepository = rideRepository;
        this.mapsService = mapsService;
        this.pricingPolicy = pricingPolicy;
        this.paymentService = paymentService;
        this.driverService = driverService;
    }

    @Transactional
    public RideResponse create(AuthenticatedUser passageiro, CreateRideRequest req) {
        RouteInfo route = mapsService.route(
                req.latOrigem().doubleValue(),
                req.lngOrigem().doubleValue(),
                req.latDestino().doubleValue(),
                req.lngDestino().doubleValue()
        );

        BigDecimal preco = pricingPolicy.calculate(route.distanciaKm(), route.tempoMin());

        Ride ride = new Ride();
        ride.setPassageiroId(passageiro.userId());
        ride.setCidade(passageiro.cidade());
        ride.setLatOrigem(req.latOrigem());
        ride.setLngOrigem(req.lngOrigem());
        ride.setLatDestino(req.latDestino());
        ride.setLngDestino(req.lngDestino());
        ride.setDistanciaKm(route.distanciaKm());
        ride.setTempoMin(route.tempoMin());
        ride.setPreco(preco);
        ride.setStatus(RideStatus.PENDING_PAYMENT);

        Ride saved = rideRepository.save(ride);
        log.info("Ride {} criada por passageiro {} (cidade={}, preco={})",
                saved.getId(), passageiro.userId(), saved.getCidade(), preco);

        return RideResponse.from(saved);
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
        return rideRepository.findMural(motorista.cidade());
    }

    @Transactional
    public RideResponse accept(AuthenticatedUser motorista, UUID rideId) {
        driverService.assertCanAcceptRides(motorista.userId());

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        if (!ride.getCidade().equals(motorista.cidade())) {
            throw new DriverCityMismatchException();
        }

        RideStateMachine.assertCanTransition(ride.getStatus(), RideStatus.DRIVER_EN_ROUTE);

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
        return RideResponse.from(ride);
    }

    @Transactional
    public RideResponse cancel(AuthenticatedUser user, UUID rideId, CancelRideRequest req) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(RideNotFoundException::new);

        String reason = req == null ? null : req.reason();

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
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(Instant.now());
        ride.setCancelReason(reason);

        log.info("Ride {} cancelada por {} (role={})", ride.getId(), user.userId(), user.role());
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

        return RideResponse.from(ride, computeDriverDistanceKm(ride));
    }

    @Transactional(readOnly = true)
    public Page<AdminRideItem> listAdminRides(Pageable pageable) {
        return rideRepository.findAllForAdmin(pageable);
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
}
