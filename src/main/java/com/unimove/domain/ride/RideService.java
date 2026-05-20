package com.unimove.domain.ride;

import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final MapsService mapsService;
    private final PricingPolicy pricingPolicy;

    public RideService(RideRepository rideRepository,
                       MapsService mapsService,
                       PricingPolicy pricingPolicy) {
        this.rideRepository = rideRepository;
        this.mapsService = mapsService;
        this.pricingPolicy = pricingPolicy;
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
}
