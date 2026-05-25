package com.unimove.domain.ride;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Job que expira corridas que ficaram paradas no mural sem nenhum motorista
 * aceitar dentro do TTL (ver {@link RideExpirationProperties}). Cada corrida e
 * expirada em sua propria transacao via {@link RideService#expireRide(UUID)},
 * de modo que uma falha (ex: lock otimista por aceite concorrente) nao afeta as
 * demais.
 *
 * Hub em instancia unica: com multiplas replicas, varias varreduras rodam em
 * paralelo, mas o load+@Version garante que cada corrida e expirada uma so vez.
 */
@Component
class RideExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RideExpirationScheduler.class);

    private final RideService rideService;
    private final RideExpirationProperties props;

    RideExpirationScheduler(RideService rideService, RideExpirationProperties props) {
        this.rideService = rideService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.ride.expiration.scan-interval-ms:60000}")
    void expireStaleRides() {
        if (!props.enabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(props.ttlMinutes()));
        List<UUID> ids = rideService.findExpirableRideIds(cutoff);
        if (ids.isEmpty()) {
            return;
        }

        int expired = 0;
        for (UUID id : ids) {
            try {
                if (rideService.expireRide(id)) {
                    expired++;
                }
            } catch (ObjectOptimisticLockingFailureException race) {
                log.debug("Ride {} aceita concorrentemente durante a expiracao — ignorada.", id);
            } catch (RuntimeException e) {
                log.warn("Falha ao expirar a corrida {}", id, e);
            }
        }
        if (expired > 0) {
            log.info("{} corrida(s) expirada(s) no mural (TTL={}min).", expired, props.ttlMinutes());
        }
    }
}
