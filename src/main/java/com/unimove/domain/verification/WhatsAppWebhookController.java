package com.unimove.domain.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Recebe as mensagens que os usuarios enviam para o numero da UniMove.
 *
 * E o unico ponto do sistema que aprende um telefone verificado: o campo
 * {@code from} vem da Meta, nao do cliente, e por isso a assinatura do payload
 * precisa ser conferida antes de qualquer coisa.
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final PhoneVerificationService service;
    private final WhatsAppProperties whatsAppProps;
    private final PhoneVerificationProperties verificationProps;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(PhoneVerificationService service,
                                     WhatsAppProperties whatsAppProps,
                                     PhoneVerificationProperties verificationProps,
                                     ObjectMapper objectMapper) {
        this.service = service;
        this.whatsAppProps = whatsAppProps;
        this.verificationProps = verificationProps;
        this.objectMapper = objectMapper;
    }

    /**
     * Handshake que a Meta faz uma vez, ao cadastrar a URL do webhook: ela
     * espera o {@code hub.challenge} de volta em texto puro.
     */
    @GetMapping
    @Operation(summary = "Handshake de verificação do webhook (Meta)")
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
                                         @RequestParam("hub.verify_token") String verifyToken,
                                         @RequestParam("hub.challenge") String challenge) {
        String expected = whatsAppProps.webhookVerifyToken();
        if (!"subscribe".equals(mode) || expected == null || expected.isBlank()
                || !constantTimeEquals(expected, verifyToken)) {
            log.warn("Handshake de webhook recusado (mode={}).", mode);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge);
    }

    /**
     * Sempre responde 200 quando a assinatura confere, mesmo que a mensagem nao
     * interesse: qualquer outro status faz a Meta reenviar o evento em loop.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recebe mensagens da WhatsApp Cloud API",
            description = "Valida `X-Hub-Signature-256` (HMAC-SHA256 do corpo cru com o app secret) "
                    + "e conclui os desafios de verificação. Sempre responde 200 quando a assinatura confere.")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (!signatureValid(signature, rawBody)) {
            log.warn("Payload de webhook com assinatura invalida — descartado.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode message : change.path("value").path("messages")) {
                        service.handleInboundMessage(
                                message.path("from").asText(null),
                                message.path("text").path("body").asText(null));
                    }
                }
            }
        } catch (Exception e) {
            // Payload malformado nao deve virar retry infinito da Meta.
            log.error("Falha ao processar payload do webhook do WhatsApp", e);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Com {@code channel=LOG} (dev/testes) nao ha app secret, entao a conferencia
     * e pulada — e o que permite simular a Meta com um POST manual. Em
     * {@code channel=WHATSAPP} o secret e obrigatorio no startup, logo a
     * assinatura e sempre exigida em producao.
     */
    private boolean signatureValid(String signatureHeader, byte[] rawBody) {
        if (verificationProps.channel() != PhoneVerificationProperties.Channel.WHATSAPP) {
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    whatsAppProps.appSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            return constantTimeEquals(expected, signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (Exception e) {
            log.error("Falha ao calcular HMAC do webhook do WhatsApp", e);
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
