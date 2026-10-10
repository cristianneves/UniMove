package com.unimove.domain.ride;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RidePrivacyServiceTest {

    @Mock RideRepository rideRepository;
    @Mock RideRatingRepository ratingRepository;
    @InjectMocks RidePrivacyService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("corrida ativa como passageiro conta")
    void activeAsPassenger() {
        when(rideRepository.existsByPassageiroIdAndStatusIn(eq(userId), anyCollection())).thenReturn(true);
        assertThat(service.hasActiveRide(userId)).isTrue();
    }

    @Test
    @DisplayName("corrida ativa como motorista conta")
    void activeAsDriver() {
        when(rideRepository.existsByMotoristaIdAndStatusIn(eq(userId), anyCollection())).thenReturn(true);
        assertThat(service.hasActiveRide(userId)).isTrue();
    }

    @Test
    @DisplayName("sem corrida ativa em nenhum papel → false")
    void noActiveRide() {
        assertThat(service.hasActiveRide(userId)).isFalse();
    }

    @Test
    @DisplayName("apaga so os comentarios escritos pelo usuario")
    void eraseComments() {
        when(ratingRepository.clearCommentsWrittenBy(userId)).thenReturn(2);
        assertThat(service.eraseRatingComments(userId)).isEqualTo(2);
    }
}
