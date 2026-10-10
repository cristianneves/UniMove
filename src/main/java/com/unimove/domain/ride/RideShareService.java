package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.ShareLinkResponse;
import com.unimove.domain.ride.dto.SharedRideResponse;
import com.unimove.domain.ride.dto.StopPoint;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.UserAccountService;
import com.unimove.domain.user.dto.DriverPublicInfo;
import com.unimove.domain.user.dto.PassengerPublicInfo;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.util.Haversine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class RideShareService {

    private final RideRepository rideRepository;
    private final UserAccountService userAccountService;
    private final DriverService driverService;

    public RideShareService(RideRepository rideRepository,
                            UserAccountService userAccountService,
                            DriverService driverService) {
        this.rideRepository = rideRepository;
        this.userAccountService = userAccountService;
        this.driverService = driverService;
    }

    /**
     * Link para o passageiro mandar a quem acompanha a viagem. So o dono o
     * recebe (o motorista tambem le RideResponse, por isso o token nao vai la)
     * e so enquanto a corrida esta viva.
     */
    @Transactional(readOnly = true)
    public ShareLinkResponse getLink(AuthenticatedUser passageiro, UUID rideId) {
        Ride ride = rideRepository.findById(rideId).orElseThrow(RideNotFoundException::new);
        if (passageiro.role() != Role.PASSAGEIRO || !ride.getPassageiroId().equals(passageiro.userId())) {
            throw new RideAccessDeniedException();
        }
        assertShareable(ride);
        return ShareLinkResponse.of(ride.getShareToken());
    }

    @Transactional(readOnly = true)
    public SharedRideResponse getByToken(UUID token) {
        Ride ride = rideRepository.findByShareToken(token)
                .orElseThrow(ShareLinkNotFoundException::new);

        assertShareable(ride);

        PassengerPublicInfo passenger = userAccountService.findPublicInfo(ride.getPassageiroId())
                .orElse(new PassengerPublicInfo(null));

        DriverPublicInfo driver = ride.getMotoristaId() == null
                ? null
                : driverService.findPublicInfo(ride.getMotoristaId()).orElse(null);

        List<StopPoint> stops = ride.getStops().stream()
                .map(s -> new StopPoint(s.getLat(), s.getLng()))
                .toList();

        return new SharedRideResponse(
                ride.getStatus(),
                ride.getCidade(),
                ride.getCategory(),
                ride.getLatOrigem(),
                ride.getLngOrigem(),
                ride.getLatDestino(),
                ride.getLngDestino(),
                stops,
                ride.getRouteGeometry(),
                passenger.firstName(),
                driver == null ? null : driver.firstName(),
                driver == null ? null : driver.vehiclePlate(),
                driver == null ? null : driver.vehicleType(),
                driver == null ? null : driver.ratingAvg(),
                driver == null ? null : driver.ratingCount(),
                ride.getDriverCurrentLat(),
                ride.getDriverCurrentLng(),
                ride.getDriverLocationUpdatedAt(),
                computeDriverDistanceKm(ride),
                ride.getCreatedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt()
        );
    }

    private static void assertShareable(Ride ride) {
        if (ride.getStatus() == RideStatus.COMPLETED
                || ride.getStatus() == RideStatus.CANCELLED
                || ride.getStatus() == RideStatus.EXPIRED) {
            throw new ShareLinkExpiredException();
        }
    }

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
