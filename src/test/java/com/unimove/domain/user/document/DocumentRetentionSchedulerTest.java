package com.unimove.domain.user.document;

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
import static org.mockito.Mockito.when;

class DocumentRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-10T06:30:00Z");
    private final DriverDocumentService service = mock(DriverDocumentService.class);

    @Test
    @DisplayName("apaga arquivos obsoletos com corte de 90 dias")
    void purgesWithNinetyDayCutoff() {
        new DocumentRetentionScheduler(service, props(true), Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        verify(service).purgeObsoleteFiles(Instant.parse("2026-07-12T06:30:00Z"));
    }

    @Test
    @DisplayName("storage fora nao derruba o job (tenta no proximo ciclo)")
    void storageFailureIsSwallowed() {
        when(service.purgeObsoleteFiles(any())).thenThrow(new DocumentStorageUnavailableException());

        new DocumentRetentionScheduler(service, props(true), Clock.fixed(NOW, ZoneOffset.UTC)).purge();
    }

    @Test
    @DisplayName("desligado nao toca no storage")
    void disabled() {
        new DocumentRetentionScheduler(service, props(false), Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        verify(service, never()).purgeObsoleteFiles(any());
    }

    private static RetentionProperties props(boolean enabled) {
        return new RetentionProperties(enabled, 5, 90, "0 30 3 * * *");
    }
}
