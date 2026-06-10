package com.unimove.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários do {@link LoginAttemptService}.
 *
 * Usa um relógio mutável para simular a passagem do tempo (expiração do
 * bloqueio e da janela de contagem de falhas) sem sleeps.
 */
class LoginAttemptServiceTest {

    private static final String EMAIL = "p@example.com";
    private static final Instant T0 = Instant.parse("2026-06-10T12:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final LoginLockoutProperties props = new LoginLockoutProperties(true, 5, 15, 600_000);
    private final LoginAttemptService service = new LoginAttemptService(props, clock);

    @Test
    @DisplayName("abaixo do limite de falhas o login não é bloqueado")
    void underThresholdDoesNotLock() {
        recordFailures(4);

        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bloqueia após o número máximo de falhas consecutivas")
    void locksAfterMaxAttempts() {
        recordFailures(5);

        assertThatThrownBy(() -> service.assertNotLocked(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("15 minuto");
    }

    @Test
    @DisplayName("login bem-sucedido zera o contador de falhas")
    void successResetsCounter() {
        recordFailures(4);
        service.recordSuccess(EMAIL);
        recordFailures(4);

        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bloqueio expira após lockout-minutes e o contador recomeça")
    void lockExpiresAfterLockoutWindow() {
        recordFailures(5);
        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();

        // Uma falha nova após a expiração conta como a primeira, não como a sexta.
        service.recordFailure(EMAIL);
        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("falhas fora da janela de contagem não acumulam")
    void staleFailuresDoNotAccumulate() {
        recordFailures(4);
        clock.advance(Duration.ofMinutes(15));
        recordFailures(4);

        assertThatCode(() -> service.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("com a proteção desabilitada nunca bloqueia")
    void disabledNeverLocks() {
        LoginAttemptService disabled =
                new LoginAttemptService(new LoginLockoutProperties(false, 5, 15, 600_000), clock);
        for (int i = 0; i < 20; i++) {
            disabled.recordFailure(EMAIL);
        }

        assertThatCode(() -> disabled.assertNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("limpeza periódica não remove bloqueios ainda vigentes")
    void purgeKeepsActiveLocks() {
        recordFailures(5);
        clock.advance(Duration.ofMinutes(1));

        service.purgeStaleEntries();

        assertThatThrownBy(() -> service.assertNotLocked(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("mensagem do bloqueio informa o tempo restante arredondado para cima")
    void lockMessageReportsRemainingMinutes() {
        recordFailures(5);
        clock.advance(Duration.ofMinutes(12).plusSeconds(30));

        assertThatThrownBy(() -> service.assertNotLocked(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("3 minuto");
    }

    private void recordFailures(int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(EMAIL);
        }
    }

    /** Relógio fixo que pode ser avançado manualmente durante o teste. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
