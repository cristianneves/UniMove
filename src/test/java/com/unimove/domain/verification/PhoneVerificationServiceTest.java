package com.unimove.domain.verification;

import com.unimove.domain.verification.channel.LogOnlyChannel;
import com.unimove.domain.verification.dto.ChallengeResponse;
import com.unimove.domain.verification.dto.ChallengeStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regras da verificação de telefone. O relógio é fixo e avançável para exercitar
 * os TTLs sem depender de tempo real (mesmo padrão de LoginAttemptServiceTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhoneVerificationServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-03T12:00:00Z");
    private static final String PHONE = "5574999990000";

    @Mock PhoneVerificationRepository repository;
    @Mock PhoneRegistry phoneRegistry;

    private MutableClock clock;
    private PhoneVerificationService service;
    private PhoneVerification saved;

    /** Relógio que o teste avança à vontade. */
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

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        PhoneVerificationProperties props =
                new PhoneVerificationProperties(PhoneVerificationProperties.Channel.LOG, 10, 15, 20, 600000L);
        service = new PhoneVerificationService(repository, new LogOnlyChannel(), phoneRegistry,
                new ChallengeRateLimiter(props, clock), props, clock);

        // O save do JPA persiste a entidade recebida; o teste guarda a referência
        // para simular findByCode/findById depois.
        when(repository.save(any(PhoneVerification.class))).thenAnswer(inv -> {
            saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(repository.existsByCode(anyString())).thenReturn(false);
    }

    private ChallengeResponse createChallenge() {
        ChallengeResponse resp = service.createChallenge("10.0.0.1");
        when(repository.findByCode(saved.getCode())).thenReturn(Optional.of(saved));
        when(repository.findById(saved.getId())).thenReturn(Optional.of(saved));
        return resp;
    }

    @Test
    @DisplayName("desafio nasce PENDING, com link wa.me e prazo do TTL configurado")
    void createChallengeStartsPending() {
        ChallengeResponse resp = createChallenge();

        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.PENDING);
        assertThat(saved.getCode()).hasSize(8).matches("[A-Z0-9]{8}");
        assertThat(resp.waLink()).startsWith("https://wa.me/").contains("UNIMOVE-" + saved.getCode());
        assertThat(resp.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        assertThat(saved.getVerifiedPhone()).isNull();
        assertThat(saved.getToken()).isNull();
    }

    @Test
    @DisplayName("mensagem com o código verifica e grava o telefone que a Meta enviou")
    void inboundMessageVerifiesUsingSenderPhone() {
        createChallenge();
        when(phoneRegistry.isPhoneRegistered(PHONE)).thenReturn(false);

        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());

        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.VERIFIED);
        assertThat(saved.getVerifiedPhone()).isEqualTo(PHONE);
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getTokenExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("texto em minúsculas e com conversa em volta ainda casa o código")
    void inboundMessageIsNormalizedBeforeMatching() {
        createChallenge();

        service.handleInboundMessage(PHONE, "oi, meu codigo e unimove-" + saved.getCode() + " obrigado");

        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("mensagem sem código nenhum é ignorada sem erro")
    void inboundMessageWithoutCodeIsIgnored() {
        createChallenge();

        service.handleInboundMessage(PHONE, "bom dia, voces atendem em Remanso?");

        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.PENDING);
    }

    @Test
    @DisplayName("telefone que já tem conta é recusado com PHONE_IN_USE")
    void inboundMessageFromRegisteredPhoneIsRejected() {
        createChallenge();
        when(phoneRegistry.isPhoneRegistered(PHONE)).thenReturn(true);

        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());

        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo(RejectionReason.PHONE_IN_USE);
        assertThat(saved.getToken()).isNull();
    }

    @Test
    @DisplayName("mensagem que chega depois do prazo expira o desafio")
    void inboundMessageAfterTtlExpires() {
        createChallenge();
        clock.advance(Duration.ofMinutes(11));

        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());

        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.EXPIRED);
        assertThat(saved.getVerifiedPhone()).isNull();
    }

    @Test
    @DisplayName("reentrega do mesmo evento pela Meta não sobrescreve o resultado")
    void duplicateWebhookDeliveryIsIgnored() {
        createChallenge();
        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());
        String firstToken = saved.getToken();

        service.handleInboundMessage("5511888887777", "UNIMOVE-" + saved.getCode());

        assertThat(saved.getVerifiedPhone()).isEqualTo(PHONE);
        assertThat(saved.getToken()).isEqualTo(firstToken);
    }

    @Test
    @DisplayName("status só entrega o token quando VERIFIED, e mascara o telefone")
    void statusExposesTokenOnlyWhenVerified() {
        createChallenge();
        ChallengeStatusResponse pending = service.getStatus(saved.getId());
        assertThat(pending.status()).isEqualTo(PhoneVerificationStatus.PENDING);
        assertThat(pending.verificationToken()).isNull();
        assertThat(pending.phone()).isNull();

        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());

        ChallengeStatusResponse verified = service.getStatus(saved.getId());
        assertThat(verified.status()).isEqualTo(PhoneVerificationStatus.VERIFIED);
        assertThat(verified.verificationToken()).isEqualTo(saved.getToken());
        assertThat(verified.phone()).isEqualTo("(74) 9****-0000");
    }

    @Test
    @DisplayName("status reporta EXPIRED assim que vence, sem esperar o scheduler")
    void statusReportsExpiredBeforeSchedulerRuns() {
        createChallenge();
        clock.advance(Duration.ofMinutes(11));

        assertThat(service.getStatus(saved.getId()).status())
                .isEqualTo(PhoneVerificationStatus.EXPIRED);
        // O banco ainda diz PENDING — quem normaliza é o scheduler.
        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.PENDING);
    }

    @Test
    @DisplayName("consumo devolve o telefone verificado e marca o desafio como usado")
    void consumeReturnsVerifiedPhone() {
        createChallenge();
        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());
        when(repository.findByToken(saved.getToken())).thenReturn(Optional.of(saved));

        String phone = service.consumeVerifiedPhone(saved.getToken());

        assertThat(phone).isEqualTo(PHONE);
        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.CONSUMED);
        assertThat(saved.getToken()).isNull();
    }

    @Test
    @DisplayName("token não pode ser usado duas vezes")
    void consumeIsSingleUse() {
        createChallenge();
        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());
        String token = saved.getToken();
        when(repository.findByToken(token)).thenReturn(Optional.of(saved));

        service.consumeVerifiedPhone(token);

        // Após o consumo o token some da entidade; ainda assim, se alguém
        // reapresentá-lo, o status CONSUMED barra.
        assertThatThrownBy(() -> service.consumeVerifiedPhone(token))
                .isInstanceOf(PhoneVerificationRequiredException.class);
    }

    @Test
    @DisplayName("token vencido é recusado")
    void consumeRejectsExpiredToken() {
        createChallenge();
        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());
        when(repository.findByToken(saved.getToken())).thenReturn(Optional.of(saved));
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> service.consumeVerifiedPhone(saved.getToken()))
                .isInstanceOf(PhoneVerificationRequiredException.class);
        assertThat(saved.getStatus()).isEqualTo(PhoneVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("token inexistente, nulo ou vazio é recusado")
    void consumeRejectsUnknownToken() {
        when(repository.findByToken("inventado")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeVerifiedPhone("inventado"))
                .isInstanceOf(PhoneVerificationRequiredException.class);
        assertThatThrownBy(() -> service.consumeVerifiedPhone(null))
                .isInstanceOf(PhoneVerificationRequiredException.class);
        assertThatThrownBy(() -> service.consumeVerifiedPhone("  "))
                .isInstanceOf(PhoneVerificationRequiredException.class);
    }

    @Test
    @DisplayName("desafio recusado não vira conta")
    void rejectedChallengeCannotBeConsumed() {
        createChallenge();
        when(phoneRegistry.isPhoneRegistered(PHONE)).thenReturn(true);
        service.handleInboundMessage(PHONE, "UNIMOVE-" + saved.getCode());
        when(repository.findByToken(any())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.consumeVerifiedPhone("qualquer-coisa"))
                .isInstanceOf(PhoneVerificationRequiredException.class);
    }

    @Test
    @DisplayName("máscara de telefone esconde o miolo do número")
    void maskPhoneHidesMiddleDigits() {
        assertThat(PhoneVerificationService.maskPhone("5574999990000")).isEqualTo("(74) 9****-0000");
        assertThat(PhoneVerificationService.maskPhone(null)).isNull();
    }
}
