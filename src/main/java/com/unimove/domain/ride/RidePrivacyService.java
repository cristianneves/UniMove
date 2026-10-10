package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.RideHistoryItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class RidePrivacyService implements RidePrivacyPort {

    /** Mesmos estados "vivos" do RideService: a corrida ainda pode mudar. */
    private static final List<RideStatus> ACTIVE = List.of(
            RideStatus.PENDING_PAYMENT, RideStatus.AVAILABLE_IN_MURAL,
            RideStatus.DRIVER_EN_ROUTE, RideStatus.IN_PROGRESS);

    private final RideRepository rideRepository;
    private final RideRatingRepository ratingRepository;

    RidePrivacyService(RideRepository rideRepository, RideRatingRepository ratingRepository) {
        this.rideRepository = rideRepository;
        this.ratingRepository = ratingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveRide(UUID userId) {
        return rideRepository.existsByPassageiroIdAndStatusIn(userId, ACTIVE)
                || rideRepository.existsByMotoristaIdAndStatusIn(userId, ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideHistoryItem> exportRides(UUID userId) {
        return rideRepository.findAllForParticipant(userId);
    }

    @Override
    @Transactional
    public int eraseRatingComments(UUID userId) {
        return ratingRepository.clearCommentsWrittenBy(userId);
    }
}
