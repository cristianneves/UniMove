package com.unimove.domain.verification;

import com.unimove.domain.verification.channel.ChallengeMessage;
import com.unimove.domain.verification.channel.PhoneVerificationChannel;
import com.unimove.domain.verification.dto.ChallengeResponse;
import com.unimove.domain.verification.dto.ChallengeStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Verificacao de posse de telefone por fluxo reverso no WhatsApp.
 *
 * O usuario envia o codigo para o numero da UniMove; a Meta entrega a mensagem
 * no webhook junto com o wa_id do remetente. O telefone da conta vem desse
 * wa_id — nunca de um campo preenchido pelo usuario —, o que impede alguem de
 * cadastrar o numero de outra pessoa.
 */
@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    // Sem caracteres ambiguos (0/O, 1/I). Maiusculas apenas: o texto recebido e
    // normalizado antes de casar, entao o usuario pode redigitar em minusculas.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_MAX_ATTEMPTS = 5;
    private static final int TOKEN_BYTES = 32;

    private final PhoneVerificationRepository repository;
    private final PhoneVerificationChannel channel;
    private final PhoneRegistry phoneRegistry;
    private final ChallengeRateLimiter rateLimiter;
    private final PhoneVerificationProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(PhoneVerificationRepository repository,
                                    PhoneVerificationChannel channel,
                                    PhoneRegistry phoneRegistry,
                                    ChallengeRateLimiter rateLimiter,
                                    PhoneVerificationProperties props,
                                    Clock clock) {
        this.repository = repository;
        this.channel = channel;
        this.phoneRegistry = phoneRegistry;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public ChallengeResponse createChallenge(String clientIp) {
        rateLimiter.assertWithinLimit(clientIp);

        Instant now = clock.instant();
        PhoneVerification verification = new PhoneVerification();
        verification.setCode(generateUniqueCode());
        verification.setStatus(PhoneVerificationStatus.PENDING);
        verification.setExpiresAt(now.plus(Duration.ofMinutes(props.challengeTtlMinutes())));
        repository.save(verification);

        log.info("Desafio de telefone criado: challengeId={}", verification.getId());
        return new ChallengeResponse(
                verification.getId(),
                verification.getCode(),
                channel.buildChallengeLink(verification.getCode()),
                verification.getExpiresAt()
        );
    }

    /**
     * Processa uma mensagem recebida pelo webhook da Meta.
     *
     * Nunca lanca: mensagem sem codigo, codigo inexistente ou desafio ja
     * resolvido sao situacoes normais (gente escreve no numero do suporte, a
     * Meta reentrega o mesmo evento). O webhook precisa responder 200 sempre,
     * senao a Meta reenvia em loop.
     */
    @Transactional
    public void handleInboundMessage(String fromPhone, String messageText) {
        String code = ChallengeMessage.extractCode(messageText);
        if (code == null || fromPhone == null || fromPhone.isBlank()) {
            return;
        }

        PhoneVerification verification = repository.findByCode(code).orElse(null);
        if (verification == null) {
            log.debug("Mensagem recebida com codigo desconhecido — ignorada.");
            return;
        }
        if (verification.getStatus() != PhoneVerificationStatus.PENDING) {
            log.debug("Desafio {} ja resolvido ({}) — reentrega ignorada.",
                    verification.getId(), verification.getStatus());
            return;
        }

        Instant now = clock.instant();
        if (now.isAfter(verification.getExpiresAt())) {
            verification.setStatus(PhoneVerificationStatus.EXPIRED);
            log.info("Desafio {} recebido apos o vencimento.", verification.getId());
            return;
        }

        if (phoneRegistry.isPhoneRegistered(fromPhone)) {
            verification.setStatus(PhoneVerificationStatus.REJECTED);
            verification.setRejectionReason(RejectionReason.PHONE_IN_USE);
            log.info("Desafio {} recusado: telefone ja possui conta.", verification.getId());
            return;
        }

        verification.setStatus(PhoneVerificationStatus.VERIFIED);
        verification.setVerifiedPhone(fromPhone);
        verification.setToken(generateToken());
        verification.setTokenExpiresAt(now.plus(Duration.ofMinutes(props.tokenTtlMinutes())));
        log.info("Desafio {} verificado.", verification.getId());
    }

    @Transactional(readOnly = true)
    public ChallengeStatusResponse getStatus(UUID challengeId) {
        PhoneVerification verification = repository.findById(challengeId)
                .orElseThrow(() -> new PhoneVerificationRequiredException(
                        "Desafio de verificacao nao encontrado."));

        // Vencido mas ainda PENDING no banco (o scheduler roda em intervalos):
        // reporta EXPIRED para o app nao ficar em polling eterno.
        PhoneVerificationStatus status = verification.getStatus();
        if (status == PhoneVerificationStatus.PENDING
                && clock.instant().isAfter(verification.getExpiresAt())) {
            status = PhoneVerificationStatus.EXPIRED;
        }

        boolean verified = status == PhoneVerificationStatus.VERIFIED;
        return new ChallengeStatusResponse(
                status,
                verified ? maskPhone(verification.getVerifiedPhone()) : null,
                verified ? verification.getToken() : null,
                verification.getRejectionReason()
        );
    }

    /**
     * Troca o token por um telefone confirmado e marca o desafio como usado.
     * Unica porta de entrada para {@code domain.user} — devolve String, sem
     * vazar entidade JPA entre dominios.
     *
     * Roda dentro da transacao do cadastro: se o register falhar depois (e-mail
     * duplicado, cidade invalida), o rollback devolve o token e o usuario
     * corrige o formulario sem precisar verificar de novo.
     *
     * @return telefone em E.164, como a Meta entregou.
     */
    @Transactional
    public String consumeVerifiedPhone(String token) {
        if (token == null || token.isBlank()) {
            throw new PhoneVerificationRequiredException();
        }
        PhoneVerification verification = repository.findByToken(token)
                .orElseThrow(PhoneVerificationRequiredException::new);

        if (verification.getStatus() != PhoneVerificationStatus.VERIFIED) {
            throw new PhoneVerificationRequiredException(
                    "Esta verificacao ja foi utilizada. Refaca a verificacao pelo WhatsApp.");
        }
        if (clock.instant().isAfter(verification.getTokenExpiresAt())) {
            throw new PhoneVerificationRequiredException(
                    "A verificacao expirou. Refaca a verificacao pelo WhatsApp.");
        }

        verification.setStatus(PhoneVerificationStatus.CONSUMED);
        verification.setToken(null);
        log.info("Desafio {} consumido no cadastro.", verification.getId());
        return verification.getVerifiedPhone();
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!repository.existsByCode(candidate)) {
                return candidate;
            }
        }
        // 32^8 combinacoes com purga de 24h: chegar aqui indica defeito, nao azar.
        throw new IllegalStateException("Nao foi possivel gerar um codigo de verificacao unico.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Ex.: 5574999998888 -> (74) 9****-8888. So para conferencia visual. */
    static String maskPhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.length() < 6) {
            return phoneE164;
        }
        String withoutCountry = phoneE164.startsWith("55") && phoneE164.length() > 10
                ? phoneE164.substring(2)
                : phoneE164;
        if (withoutCountry.length() < 6) {
            return "****" + phoneE164.substring(phoneE164.length() - 4);
        }
        String ddd = withoutCountry.substring(0, 2);
        String head = withoutCountry.substring(2, 3);
        String tail = withoutCountry.substring(withoutCountry.length() - 4);
        return "(" + ddd + ") " + head + "****-" + tail;
    }
}
