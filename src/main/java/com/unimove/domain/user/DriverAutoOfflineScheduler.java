package com.unimove.domain.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Job que marca offline motoristas online sem atividade dentro da janela
 * configurada (ver {@link DriverPresenceProperties}). O LastSeenInterceptor
 * renova o lastSeenAt a cada request autenticado do motorista; quem fecha o
 * app sem tocar em "ficar offline" para de dar sinal e, sem este job, contaria
 * como disponivel para sempre (mural "fantasma").
 *
 * UPDATE em lote idempotente: com multiplas replicas, varreduras concorrentes
 * apenas repetem um no-op.
 */
@Component
class DriverAutoOfflineScheduler {

    private static final Logger log = LoggerFactory.getLogger(DriverAutoOfflineScheduler.class);

    private final DriverService driverService;
    private final DriverPresenceProperties props;

    DriverAutoOfflineScheduler(DriverService driverService, DriverPresenceProperties props) {
        this.driverService = driverService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.driver.presence.scan-interval-ms:60000}")
    void markStaleDriversOffline() {
        if (!props.enabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(props.offlineAfterMinutes()));
        int n = driverService.markStaleDriversOffline(cutoff);
        if (n > 0) {
            log.info("{} motorista(s) marcado(s) offline por inatividade (>{}min sem request).",
                    n, props.offlineAfterMinutes());
        }
    }
}
