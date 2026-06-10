package com.unimove.domain.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protecao contra brute-force no login: apos {@code maxAttempts} falhas
 * consecutivas para o mesmo e-mail, o login fica bloqueado por
 * {@code lockoutMinutes} (ver {@link LoginLockoutProperties}).
 *
 * Estado em memoria (ConcurrentHashMap) — suficiente para o MVP em instancia
 * unica. Com multiplas replicas o contador passa a ser por replica, o que
 * apenas enfraquece (nao quebra) a protecao.
 */
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final LoginLockoutProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant lastFailureAt, Instant lockedUntil) {}

    public LoginAttemptService(LoginLockoutProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /**
     * Lanca {@link TooManyLoginAttemptsException} (HTTP 429) se o e-mail
     * estiver bloqueado. Deve ser chamado antes de validar as credenciais.
     */
    public void assertNotLocked(String email) {
        if (!props.enabled()) {
            return;
        }
        Attempts attempts = attemptsByEmail.get(email);
        if (attempts == null || attempts.lockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(attempts.lockedUntil())) {
            long secondsRemaining = Duration.between(now, attempts.lockedUntil()).getSeconds();
            throw new TooManyLoginAttemptsException((secondsRemaining + 59) / 60);
        }
        attemptsByEmail.remove(email);
    }

    public void recordFailure(String email) {
        if (!props.enabled()) {
            return;
        }
        Instant now = clock.instant();
        Attempts updated = attemptsByEmail.compute(email, (key, prev) -> {
            int failures = (prev == null || isStale(prev, now)) ? 1 : prev.failures() + 1;
            Instant lockedUntil = failures >= props.maxAttempts()
                    ? now.plus(Duration.ofMinutes(props.lockoutMinutes()))
                    : null;
            return new Attempts(failures, now, lockedUntil);
        });
        if (updated.lockedUntil() != null) {
            log.warn("Login bloqueado por {} min para o e-mail {} apos {} falhas consecutivas.",
                    props.lockoutMinutes(), email, updated.failures());
        }
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(email);
    }

    // Falhas antigas (fora da janela de lockoutMinutes) nao contam mais.
    private boolean isStale(Attempts attempts, Instant now) {
        return attempts.lockedUntil() == null
                && Duration.between(attempts.lastFailureAt(), now).toMinutes() >= props.lockoutMinutes();
    }

    @Scheduled(fixedDelayString = "${app.auth.lockout.cleanup-interval-ms:600000}")
    void purgeStaleEntries() {
        Instant now = clock.instant();
        attemptsByEmail.entrySet().removeIf(entry -> {
            Attempts attempts = entry.getValue();
            Instant expiry = attempts.lockedUntil() != null
                    ? attempts.lockedUntil()
                    : attempts.lastFailureAt().plus(Duration.ofMinutes(props.lockoutMinutes()));
            return now.isAfter(expiry);
        });
    }
}
