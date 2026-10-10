package com.unimove.it;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class SessionAndDocsIT extends IntegrationTestSupport {

    @Test
    @DisplayName("troca de cidade: token antigo → 401, token novo → 200")
    void cityChangeRevokesOldTokens() throws InterruptedException {
        User passenger = newUser(Role.PASSAGEIRO);
        String oldToken = token(passenger);
        // iat do JWT tem precisao de segundo: o token "antigo" precisa ser de
        // um segundo anterior ao da troca para ser revogado.
        Thread.sleep(1100);
        String newCity = "it-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO served_cities (slug, display_name) VALUES (?, ?)", newCity, newCity);

        HttpResponse<String> changed = send(putJson("/users/me", oldToken,
                "{\"name\":\"IT\",\"cidade\":\"" + newCity + "\"}"));
        assertThat(changed.statusCode()).isEqualTo(200);
        String newToken = json(changed).get("token").asText();

        assertThat(get("/users/me", oldToken).statusCode()).isEqualTo(401);
        HttpResponse<String> me = get("/users/me", newToken);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json(me).get("cidade").asText()).isEqualTo(newCity);
    }

    @Test
    @DisplayName("Swagger: sem login redireciona; passageiro nao entra; admin entra e le o /v3/api-docs")
    void swaggerRequiresAdminLogin() {
        HttpResponse<String> anonymous = browser().get("/v3/api-docs");
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(anonymous.headers().firstValue("Location")).hasValue("/docs/login");

        Browser passenger = browser();
        HttpResponse<String> denied = passenger.login(newUser(Role.PASSAGEIRO).getEmail());
        assertThat(denied.headers().firstValue("Location")).hasValue("/docs/login?error");
        assertThat(passenger.get("/v3/api-docs").statusCode()).isEqualTo(302);

        Browser admin = browser();
        HttpResponse<String> ok = admin.login(newUser(Role.ADMIN).getEmail());
        assertThat(ok.headers().firstValue("Location")).hasValue("/swagger-ui/index.html");
        HttpResponse<String> docs = admin.get("/v3/api-docs");
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("\"openapi\"");
        assertThat(admin.get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    private Browser browser() {
        return new Browser(HttpClient.newBuilder().cookieHandler(new CookieManager()).build());
    }

    /** Cliente com cookies, como um navegador: GET do formulario, POST com CSRF. */
    private final class Browser {
        private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
        private final HttpClient client;

        Browser(HttpClient client) {
            this.client = client;
        }

        HttpResponse<String> get(String path) {
            return call(HttpRequest.newBuilder(uri(path)).GET().build());
        }

        HttpResponse<String> login(String email) {
            Matcher m = CSRF.matcher(get("/docs/login").body());
            assertThat(m.find()).isTrue();
            String form = "email=" + enc(email) + "&password=" + enc(PASSWORD) + "&_csrf=" + enc(m.group(1));
            return call(HttpRequest.newBuilder(uri("/docs/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        }

        private HttpResponse<String> call(HttpRequest request) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private static String enc(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
    }
}
