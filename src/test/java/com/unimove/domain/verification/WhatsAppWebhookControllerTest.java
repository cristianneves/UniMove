package com.unimove.domain.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O webhook é o único ponto que aprende um telefone verificado, e é público.
 * Estes testes cobrem a validação da assinatura da Meta e o contrato de sempre
 * responder 200 (qualquer outro status faz a Meta reenviar em loop).
 */
@WebMvcTest(controllers = WhatsAppWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(WhatsAppWebhookControllerTest.TestConfig.class)
class WhatsAppWebhookControllerTest {

    private static final String APP_SECRET = "segredo-do-app-meta";
    private static final String VERIFY_TOKEN = "token-do-handshake";

    static class TestConfig {
        // Canal WHATSAPP para exercitar a validação de assinatura, que em LOG é pulada.
        @Bean @Primary PhoneVerificationProperties phoneVerificationProperties() {
            return new PhoneVerificationProperties(
                    PhoneVerificationProperties.Channel.WHATSAPP, 10, 15, 20, 600000L);
        }

        @Bean @Primary WhatsAppProperties whatsAppProperties() {
            return new WhatsAppProperties("5574999990000", APP_SECRET, VERIFY_TOKEN);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PhoneVerificationService service;
    @MockBean com.unimove.shared.security.JwtService jwtService;
    @MockBean com.unimove.shared.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    // Exigido pelo LastSeenInterceptor, registrado no WebMvcConfigurer da aplicação.
    @MockBean com.unimove.domain.user.DriverService driverService;

    private static String payload(String from, String text) {
        return """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"from":"%s","type":"text","text":{"body":"%s"}}]}}]}]}
                """.formatted(from, text);
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("payload assinado corretamente é processado")
    void validSignatureIsProcessed() throws Exception {
        String body = payload("5574999990000", "UNIMOVE-ABC12345");

        mvc.perform(post("/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", sign(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(service).handleInboundMessage(eq("5574999990000"), eq("UNIMOVE-ABC12345"));
    }

    @Test
    @DisplayName("assinatura inválida é descartada sem tocar no serviço")
    void invalidSignatureIsRejected() throws Exception {
        String body = payload("5574999990000", "UNIMOVE-ABC12345");

        mvc.perform(post("/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", "sha256=00000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        verify(service, never()).handleInboundMessage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("assinatura de outro corpo não vale para este payload")
    void signatureOfDifferentBodyIsRejected() throws Exception {
        String signedBody = payload("5574999990000", "UNIMOVE-ABC12345");
        String tamperedBody = payload("5511888887777", "UNIMOVE-ABC12345");

        mvc.perform(post("/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", sign(signedBody))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedBody))
                .andExpect(status().isForbidden());

        verify(service, never()).handleInboundMessage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("sem header de assinatura é descartado")
    void missingSignatureIsRejected() throws Exception {
        mvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("5574999990000", "UNIMOVE-ABC12345")))
                .andExpect(status().isForbidden());

        verify(service, never()).handleInboundMessage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("payload sem mensagens (ex.: status de entrega) responde 200 sem efeito")
    void payloadWithoutMessagesIsAccepted() throws Exception {
        String body = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "statuses":[{"id":"wamid.X","status":"delivered"}]}}]}]}
                """;

        mvc.perform(post("/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", sign(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(service, never()).handleInboundMessage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("JSON malformado responde 200 para a Meta não reenviar em loop")
    void malformedJsonStillReturns200() throws Exception {
        String body = "{ isso nao e json";

        mvc.perform(post("/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", sign(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("handshake devolve o challenge quando o verify token confere")
    void handshakeReturnsChallenge() throws Exception {
        mvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "1234567890"))
                .andExpect(status().isOk())
                .andExpect(content().string("1234567890"));
    }

    @Test
    @DisplayName("handshake com verify token errado é recusado")
    void handshakeWithWrongTokenIsRejected() throws Exception {
        mvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "chute")
                        .param("hub.challenge", "1234567890"))
                .andExpect(status().isForbidden());
    }
}
