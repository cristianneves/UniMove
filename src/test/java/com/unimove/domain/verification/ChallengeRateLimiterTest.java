package com.unimove.domain.verification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChallengeRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-08-03T12:00:00Z");

    private static PhoneVerificationProperties props(int maxPerHour) {
        return new PhoneVerificationProperties(
                PhoneVerificationProperties.Channel.LOG, 10, 15, maxPerHour, 600000L);
    }

    @Test
    @DisplayName("permite até o teto configurado e bloqueia a partir da próxima")
    void allowsUpToLimitThenBlocks() {
        ChallengeRateLimiter limiter =
                new ChallengeRateLimiter(props(3), Clock.fixed(T0, ZoneOffset.UTC));

        for (int i = 0; i < 3; i++) {
            int attempt = i;
            assertThatCode(() -> limiter.assertWithinLimit("10.0.0.1"))
                    .as("tentativa %d dentro do teto", attempt + 1)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.assertWithinLimit("10.0.0.1"))
                .isInstanceOf(TooManyChallengesException.class);
    }

    @Test
    @DisplayName("o teto é por IP — um IP bloqueado não afeta os outros")
    void limitIsPerIp() {
        ChallengeRateLimiter limiter =
                new ChallengeRateLimiter(props(1), Clock.fixed(T0, ZoneOffset.UTC));

        limiter.assertWithinLimit("10.0.0.1");
        assertThatThrownBy(() -> limiter.assertWithinLimit("10.0.0.1"))
                .isInstanceOf(TooManyChallengesException.class);

        assertThatCode(() -> limiter.assertWithinLimit("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a janela reabre depois de uma hora")
    void windowResetsAfterAnHour() {
        MutableClock clock = new MutableClock(T0);
        ChallengeRateLimiter limiter = new ChallengeRateLimiter(props(1), clock);

        limiter.assertWithinLimit("10.0.0.1");
        assertThatThrownBy(() -> limiter.assertWithinLimit("10.0.0.1"))
                .isInstanceOf(TooManyChallengesException.class);

        clock.advance(Duration.ofMinutes(61));

        assertThatCode(() -> limiter.assertWithinLimit("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("purga remove janelas vencidas e libera o IP")
    void purgeClearsStaleWindows() {
        MutableClock clock = new MutableClock(T0);
        ChallengeRateLimiter limiter = new ChallengeRateLimiter(props(1), clock);

        limiter.assertWithinLimit("10.0.0.1");
        clock.advance(Duration.ofMinutes(61));
        limiter.purgeStaleWindows();

        assertThatCode(() -> limiter.assertWithinLimit("10.0.0.1")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() {
            return now;
        }

        @Override public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
