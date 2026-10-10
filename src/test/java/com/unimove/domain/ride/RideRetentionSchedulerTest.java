package com.unimove.domain.ride;

import com.unimove.domain.chat.ChatPrivacyPort;
import com.unimove.shared.config.RetentionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RideRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-10T06:30:00Z");

    private final RideRepository rides = mock(RideRepository.class);
    private final RideRatingRepository ratings = mock(RideRatingRepository.class);
    private final ChatPrivacyPort chat = mock(ChatPrivacyPort.class);

    @Test
    @DisplayName("anonimiza corridas, paradas, comentarios e mensagens anteriores ao corte de 5 anos")
    void purgesWithFiveYearCutoff() {
        scheduler(true).purge();

        Instant cutoff = Instant.parse("2021-10-10T06:30:00Z");
        verify(rides).deleteStopsOfRidesCreatedBefore(cutoff);
        verify(rides).anonymizeRidesCreatedBefore(cutoff);
        verify(ratings).clearCommentsCreatedBefore(cutoff);
        verify(chat).deleteOlderThan(cutoff);
    }

    @Test
    @DisplayName("desligado nao toca em nada")
    void disabled() {
        scheduler(false).purge();

        verify(rides, never()).anonymizeRidesCreatedBefore(any());
        verify(chat, never()).deleteOlderThan(any());
    }

    private RideRetentionScheduler scheduler(boolean enabled) {
        return new RideRetentionScheduler(rides, ratings, chat,
                new RetentionProperties(enabled, 5, 90, "0 30 3 * * *"), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
