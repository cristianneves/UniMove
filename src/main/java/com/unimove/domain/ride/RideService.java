package com.unimove.domain.ride;

import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.user.DriverService;
import com.unimove.shared.security.AuthenticatedUser;
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
}
