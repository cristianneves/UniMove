package com.unimove.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do cadastro real que o app consome (cards RF01 e QA): desafio de
 * telefone pelo WhatsApp (canal LOG, webhook chamado a mao como a Meta faria),
 * {@code verificationToken}, cidade por slug e termos vigentes.
 */
@PostgresIntegrationTest
class AuthContractIT extends IntegrationTestSupport {

    @Test
    @DisplayName("fluxo feliz: cidades → desafio → webhook → VERIFIED → cadastro 201 → login → /users/me")
    void verifiedRegistration() {
        HttpResponse<String> cities = get("/cities", null);
        assertThat(cities.statusCode()).isEqualTo(200);
        assertThat(cities.body()).contains("\"slug\":\"" + city + "\"");

        String phone = randomPhone();
        JsonNode challenge = json(send(postJson("/auth/phone/challenge", null, "")));
        assertThat(challenge.get("waLink").asText()).startsWith("https://wa.me/").contains(challenge.get("code").asText());
        String challengeId = challenge.get("challengeId").asText();
        assertThat(json(get("/auth/phone/challenge/" + challengeId, null)).get("status").asText()).isEqualTo("PENDING");

        assertThat(whatsAppMessage(phone, challenge.get("code").asText()).statusCode()).isEqualTo(200);
        JsonNode verified = json(get("/auth/phone/challenge/" + challengeId, null));
        assertThat(verified.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(verified.get("phone").asText()).doesNotContain(phone.substring(4, 9));

        String email = "pass-" + UUID.randomUUID() + "@it.local";
        HttpResponse<String> registered = send(postJson("/auth/register", null,
                register(email, "PASSAGEIRO", city, verified.get("verificationToken").asText(), terms())));
        assertThat(registered.statusCode()).isEqualTo(201);
        JsonNode auth = json(registered);
        assertThat(auth.get("role").asText()).isEqualTo("PASSAGEIRO");
        assertThat(auth.get("cidade").asText()).isEqualTo(city);
        assertThat(auth.get("mustChangePassword").asBoolean()).isFalse();

        JsonNode me = json(get("/users/me", auth.get("token").asText()));
        assertThat(me.get("email").asText()).isEqualTo(email);
        assertThat(me.get("phoneVerified").asBoolean()).isTrue();
        assertThat(me.get("phone").asText()).isNotBlank();

        HttpResponse<String> login = send(postJson("/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(json(login).get("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("motorista se cadastra com veiculo e nasce nao aprovado")
    void driverRegistrationStartsUnapproved() {
        String body = "{\"email\":\"drv-" + UUID.randomUUID() + "@it.local\",\"password\":\"" + PASSWORD + "\","
                + "\"name\":\"IT\",\"verificationToken\":\"" + verifiedToken(randomPhone()) + "\","
                + "\"role\":\"MOTORISTA\",\"cidade\":\"" + city + "\",\"vehicleType\":\"MOTO\","
                + "\"vehiclePlate\":\"ABC1D23\",\"termsVersion\":\"" + terms() + "\"}";
        HttpResponse<String> registered = send(postJson("/auth/register", null, body));
        assertThat(registered.statusCode()).isEqualTo(201);

        JsonNode vehicle = json(get("/users/me", json(registered).get("token").asText())).get("vehicle");
        assertThat(vehicle.get("approved").asBoolean()).isFalse();
        assertThat(vehicle.get("online").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("token de verificacao e de uso unico; inexistente ou ausente → 400")
    void verificationTokenIsSingleUse() {
        String token = verifiedToken(randomPhone());
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, token, terms())))
                .statusCode()).isEqualTo(201);

        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, token, terms())))
                .statusCode()).isEqualTo(400);
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, "nao-existe", terms())))
                .statusCode()).isEqualTo(400);
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, "", terms())))
                .statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("cidade fora de /cities → 400 sem gastar o token; corrigida, o mesmo token cadastra")
    void invalidCityKeepsTokenUsable() {
        String token = verifiedToken(randomPhone());

        HttpResponse<String> rejected = send(postJson("/auth/register", null,
                register(newEmail(), "PASSAGEIRO", "Remanso Livre", token, terms())));
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(json(rejected).get("message").asText()).isEqualTo("Ainda não atendemos esta cidade.");

        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, token, terms())))
                .statusCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("telefone que ja tem conta → desafio REJECTED com PHONE_IN_USE e sem token")
    void phoneInUseIsRejected() {
        User existing = newUser(Role.PASSAGEIRO);
        JsonNode challenge = json(send(postJson("/auth/phone/challenge", null, "")));
        whatsAppMessage(existing.getPhone(), challenge.get("code").asText());

        JsonNode status = json(get("/auth/phone/challenge/" + challenge.get("challengeId").asText(), null));
        assertThat(status.get("status").asText()).isEqualTo("REJECTED");
        assertThat(status.get("rejectionReason").asText()).isEqualTo("PHONE_IN_USE");
        assertThat(status.get("verificationToken").isNull()).isTrue();
    }

    @Test
    @DisplayName("desafio vencido aparece EXPIRED; token vencido → 400")
    void expiredChallengeAndToken() {
        JsonNode pending = json(send(postJson("/auth/phone/challenge", null, "")));
        String pendingId = pending.get("challengeId").asText();
        jdbc.update("UPDATE phone_verifications SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", pendingId);
        assertThat(json(get("/auth/phone/challenge/" + pendingId, null)).get("status").asText()).isEqualTo("EXPIRED");

        String token = verifiedToken(randomPhone());
        jdbc.update("UPDATE phone_verifications SET token_expires_at = now() - interval '1 minute' WHERE token = ?", token);
        HttpResponse<String> late = send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, token, terms())));
        assertThat(late.statusCode()).isEqualTo(400);
        assertThat(json(late).get("message").asText()).contains("expirou");
    }

    @Test
    @DisplayName("ADMIN, termos antigos, motorista sem veiculo → 400; e-mail repetido → 409")
    void invalidRegistrations() {
        String token = verifiedToken(randomPhone());
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "ADMIN", city, token, terms())))
                .statusCode()).isEqualTo(400);
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, token, "1999-01")))
                .statusCode()).isEqualTo(400);
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "MOTORISTA", city, token, terms())))
                .statusCode()).isEqualTo(400);

        String taken = newUser(Role.PASSAGEIRO).getEmail();
        assertThat(send(postJson("/auth/register", null, register(taken, "PASSAGEIRO", city, token, terms())))
                .statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("401 sem token ou senha errada; 403 fora do papel")
    void authenticationAndRoles() {
        User passenger = newUser(Role.PASSAGEIRO);
        assertThat(send(postJson("/auth/login", null,
                "{\"email\":\"" + passenger.getEmail() + "\",\"password\":\"errada-123\"}")).statusCode()).isEqualTo(401);
        assertThat(get("/users/me", null).statusCode()).isEqualTo(401);
        assertThat(get("/users/me", "token-invalido").statusCode()).isEqualTo(401);

        assertThat(get("/admin/users", token(passenger)).statusCode()).isEqualTo(403);
        assertThat(send(postJson("/rides", token(newOperationalDriver()), RIDE_BODY)).statusCode()).isEqualTo(403);
        assertThat(get("/rides/mural", token(passenger)).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("esqueci minha senha: desafio PASSWORD_RESET → 204 → senha antiga 401, nova 200, token velho 401")
    void passwordResetWithWhatsApp() throws InterruptedException {
        User user = newUser(Role.PASSAGEIRO);
        String oldToken = token(user);
        // iat do JWT tem precisao de segundo: o token "antigo" precisa ser de
        // um segundo anterior ao do reset para ser revogado.
        Thread.sleep(1100);
        String body = "{\"purpose\":\"PASSWORD_RESET\"}";

        // Numero sem conta: recusado, sem token.
        JsonNode unknown = json(send(postJson("/auth/phone/challenge", null, body)));
        whatsAppMessage(randomPhone(), unknown.get("code").asText());
        JsonNode rejected = json(get("/auth/phone/challenge/" + unknown.get("challengeId").asText(), null));
        assertThat(rejected.get("rejectionReason").asText()).isEqualTo("PHONE_NOT_REGISTERED");

        JsonNode challenge = json(send(postJson("/auth/phone/challenge", null, body)));
        whatsAppMessage(user.getPhone(), challenge.get("code").asText());
        String resetToken = json(get("/auth/phone/challenge/" + challenge.get("challengeId").asText(), null))
                .get("verificationToken").asText();

        // Token de recuperacao nao cria conta.
        assertThat(send(postJson("/auth/register", null, register(newEmail(), "PASSAGEIRO", city, resetToken, terms())))
                .statusCode()).isEqualTo(400);

        String reset = "{\"verificationToken\":\"" + resetToken + "\",\"newPassword\":\"senha-nova-456\"}";
        assertThat(send(postJson("/auth/password/reset", null, reset)).statusCode()).isEqualTo(204);
        assertThat(send(postJson("/auth/password/reset", null, reset)).statusCode()).isEqualTo(400);

        assertThat(send(postJson("/auth/login", null,
                "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")).statusCode()).isEqualTo(401);
        assertThat(send(postJson("/auth/login", null,
                "{\"email\":\"" + user.getEmail() + "\",\"password\":\"senha-nova-456\"}")).statusCode()).isEqualTo(200);
        assertThat(get("/users/me", oldToken).statusCode()).isEqualTo(401);

        // Token de cadastro nao redefine senha.
        String signupToken = verifiedToken(randomPhone());
        assertThat(send(postJson("/auth/password/reset", null,
                "{\"verificationToken\":\"" + signupToken + "\",\"newPassword\":\"senha-nova-789\"}")).statusCode())
                .isEqualTo(400);
    }

    // ------------------------------------------------------------------------

    /** Desafio + mensagem do WhatsApp; devolve o verificationToken. */
    private String verifiedToken(String phone) {
        JsonNode challenge = json(send(postJson("/auth/phone/challenge", null, "")));
        whatsAppMessage(phone, challenge.get("code").asText());
        return json(get("/auth/phone/challenge/" + challenge.get("challengeId").asText(), null))
                .get("verificationToken").asText();
    }

    /** O que a Meta entrega ao receber a mensagem do usuario (canal LOG dispensa assinatura). */
    private HttpResponse<String> whatsAppMessage(String fromPhone, String code) {
        return send(postJson("/webhooks/whatsapp", null, """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{"messages":[
                {"from":"%s","type":"text","text":{"body":"UNIMOVE-%s"}}]}}]}]}
                """.formatted(fromPhone, code)));
    }

    private String terms() {
        return json(get("/legal/terms", null)).get("version").asText();
    }

    private static String register(String email, String role, String cidade, String token, String terms) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"IT\","
                + "\"verificationToken\":\"" + token + "\",\"role\":\"" + role + "\","
                + "\"cidade\":\"" + cidade + "\",\"termsVersion\":\"" + terms + "\"}";
    }

    private static String newEmail() {
        return "it-" + UUID.randomUUID() + "@it.local";
    }

    private static String randomPhone() {
        return "5574" + (900_000_000L + (long) (Math.random() * 99_999_999L));
    }
}
