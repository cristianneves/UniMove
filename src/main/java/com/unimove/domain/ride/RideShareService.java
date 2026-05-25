package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.SharedRideResponse;
import com.unimove.domain.ride.dto.StopPoint;
import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.UserAccountService;
import com.unimove.domain.user.dto.DriverPublicInfo;
import com.unimove.domain.user.dto.PassengerPublicInfo;
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

    @Transactional(readOnly = true)
    public SharedRideResponse getByToken(UUID token) {
        Ride ride = rideRepository.findByShareToken(token)
                .orElseThrow(ShareLinkNotFoundException::new);

        if (ride.getStatus() == RideStatus.COMPLETED
                || ride.getStatus() == RideStatus.CANCELLED
                || ride.getStatus() == RideStatus.EXPIRED) {
            throw new ShareLinkExpiredException();
        }

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
