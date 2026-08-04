package com.unimove.domain.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teto de desafios por IP por hora.
 *
 * Menos critico do que parece: criar desafio nao envia mensagem nenhuma (o
 * fluxo e reverso), entao abusar custa apenas linha em tabela — nao ha SMS
 * queimando dinheiro nem numero sendo marcado como spam. O limite existe para
 * evitar inchaco da tabela, ja que /auth/** e publico.
 *
 * Estado em memoria, mesma escolha (e mesma limitacao com multiplas replicas)
 * documentada em {@code LoginAttemptService}.
 */
@Component
public class ChallengeRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ChallengeRateLimiter.class);
    private static final Duration WINDOW = Duration.ofHours(1);

    private final PhoneVerificationProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windowsByIp = new ConcurrentHashMap<>();

    private record Window(int count, Instant startedAt) {}

    public ChallengeRateLimiter(PhoneVerificationProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Contabiliza a tentativa e lanca 429 se o IP estourou o teto na janela. */
    public void assertWithinLimit(String clientIp) {
        Instant now = clock.instant();
        Window updated = windowsByIp.compute(clientIp, (key, prev) -> {
            if (prev == null || Duration.between(prev.startedAt(), now).compareTo(WINDOW) >= 0) {
                return new Window(1, now);
            }
            return new Window(prev.count() + 1, prev.startedAt());
        });
        if (updated.count() > props.maxChallengesPerIpHour()) {
            log.warn("Teto de desafios de telefone estourado para o IP {} ({} na ultima hora).",
                    clientIp, updated.count());
            throw new TooManyChallengesException();
        }
    }

    /** Chamado pelo scheduler do dominio junto com a purga dos desafios. */
    void purgeStaleWindows() {
        Instant now = clock.instant();
        windowsByIp.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().startedAt(), now).compareTo(WINDOW) >= 0);
    }
}
