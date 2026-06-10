package com.unimove.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverAutoOfflineSchedulerTest {

    @Mock DriverService driverService;

    @Test
    @DisplayName("desabilitado: não toca no DriverService")
    void disabledDoesNothing() {
        DriverAutoOfflineScheduler scheduler = new DriverAutoOfflineScheduler(
                driverService, new DriverPresenceProperties(false, 10, 60_000));

        scheduler.markStaleDriversOffline();

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("habilitado: varre com cutoff = agora - offlineAfterMinutes")
    void scansWithCutoffOfflineAfterMinutesInThePast() {
        when(driverService.markStaleDriversOffline(any())).thenReturn(2);
        DriverAutoOfflineScheduler scheduler = new DriverAutoOfflineScheduler(
                driverService, new DriverPresenceProperties(true, 10, 60_000));

        scheduler.markStaleDriversOffline();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(driverService).markStaleDriversOffline(cutoff.capture());
        Instant now = Instant.now();
        assertThat(cutoff.getValue())
                .isBetween(now.minusSeconds(11 * 60), now.minusSeconds(9 * 60));
    }
}
