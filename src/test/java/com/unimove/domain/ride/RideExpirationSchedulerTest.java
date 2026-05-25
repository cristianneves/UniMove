package com.unimove.domain.ride;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideExpirationSchedulerTest {

    @Mock RideService rideService;

    @Test
    @DisplayName("desabilitado: não toca no RideService")
    void disabledDoesNothing() {
        RideExpirationScheduler scheduler = new RideExpirationScheduler(
                rideService, new RideExpirationProperties(false, 15, 60_000));

        scheduler.expireStaleRides();

        verifyNoInteractions(rideService);
    }

    @Test
    @DisplayName("sem candidatos: não chama expireRide")
    void noCandidatesSkipsExpireRide() {
        when(rideService.findExpirableRideIds(any())).thenReturn(List.of());
        RideExpirationScheduler scheduler = new RideExpirationScheduler(
                rideService, new RideExpirationProperties(true, 15, 60_000));

        scheduler.expireStaleRides();

        verify(rideService).findExpirableRideIds(any());
        verify(rideService, never()).expireRide(any());
    }

    @Test
    @DisplayName("expira cada candidato e tolera lock otimista (race com aceite)")
    void expiresEachCandidateAndToleratesRace() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(rideService.findExpirableRideIds(any())).thenReturn(List.of(a, b, c));
        when(rideService.expireRide(a)).thenReturn(true);
        when(rideService.expireRide(b))
                .thenThrow(new ObjectOptimisticLockingFailureException("aceite concorrente", new RuntimeException()));
        when(rideService.expireRide(c)).thenReturn(false);

        RideExpirationScheduler scheduler = new RideExpirationScheduler(
                rideService, new RideExpirationProperties(true, 15, 60_000));

        // A exceção de um candidato não pode interromper o loop nem propagar.
        assertThatCode(scheduler::expireStaleRides).doesNotThrowAnyException();

        verify(rideService).expireRide(a);
        verify(rideService).expireRide(b);
        verify(rideService).expireRide(c);
    }
}
