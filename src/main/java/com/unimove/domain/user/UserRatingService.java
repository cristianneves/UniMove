package com.unimove.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class UserRatingService {

    private final UserRepository userRepository;

    public UserRatingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RatingStats applyRating(UUID userId, int score) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado para aplicar rating: " + userId));

        int oldCount = user.getRatingCount();
        BigDecimal oldAvg = user.getRatingAvg();
        int newCount = oldCount + 1;
        BigDecimal newAvg = oldAvg.multiply(BigDecimal.valueOf(oldCount))
                .add(BigDecimal.valueOf(score))
                .divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        user.setRatingAvg(newAvg);
        user.setRatingCount(newCount);
        return new RatingStats(newAvg, newCount);
    }

    @Transactional(readOnly = true)
    public RatingStats getStats(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new RatingStats(u.getRatingAvg(), u.getRatingCount()))
                .orElse(new RatingStats(BigDecimal.ZERO, 0));
    }
}
