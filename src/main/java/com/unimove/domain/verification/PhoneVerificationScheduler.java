package com.unimove.domain.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Marca como EXPIRED os desafios vencidos e apaga os terminais antigos, para a
 * tabela nao crescer indefinidamente — ela so guarda dado efemero.
 */
@Component
public class PhoneVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationScheduler.class);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final PhoneVerificationRepository repository;
    private final ChallengeRateLimiter rateLimiter;
    private final Clock clock;

    public PhoneVerificationScheduler(PhoneVerificationRepository repository,
                                      ChallengeRateLimiter rateLimiter,
                                      Clock clock) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.phone-verification.purge-interval-ms:600000}")
    @Transactional
    public void expireAndPurge() {
        Instant now = clock.instant();
        int expired = repository.markExpired(now);
        int purged = repository.purgeTerminalOlderThan(now.minus(RETENTION));
        rateLimiter.purgeStaleWindows();
        if (expired > 0 || purged > 0) {
            log.info("Verificacoes de telefone: {} expiradas, {} removidas.", expired, purged);
        }
    }
}
