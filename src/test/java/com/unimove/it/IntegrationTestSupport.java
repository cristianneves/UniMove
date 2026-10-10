package com.unimove.it;

import com.unimove.domain.maps.MapsService;
import com.unimove.domain.maps.RouteInfo;
import com.unimove.domain.user.Driver;
import com.unimove.domain.user.DriverRepository;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.domain.user.UserRepository;
import com.unimove.domain.user.VehicleType;
import com.unimove.domain.user.document.DocumentStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Base dos IT: Postgres real (migrations Flyway aplicadas no boot, Hibernate
 * validate), API em porta aleatoria e cliente HTTP de verdade. So o mundo
 * externo e simulado (OSRM e storage de documentos). Cada teste cria os
 * proprios usuarios numa cidade propria — o banco nao precisa ser limpo.
 */
public abstract class IntegrationTestSupport {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("IT_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> env("IT_DATABASE_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("IT_DATABASE_PASSWORD", "it"));
        // Todos os IT saem do mesmo IP (localhost); os limites por IP sao
        // cobertos em RateLimitFilterTest e atrapalhariam os contratos aqui.
        registry.add("app.rate-limit.auth.max-requests", () -> "100000");
        registry.add("app.phone-verification.max-challenges-per-ip-hour", () -> "100000");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final String PASSWORD = "senha-forte-123";

    @LocalServerPort int port;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DriverRepository driverRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected com.unimove.shared.security.JwtService jwtService;
    @Autowired protected org.springframework.transaction.support.TransactionTemplate tx;

    @MockBean protected MapsService mapsService;
    @MockBean protected DocumentStorage documentStorage;

    protected final HttpClient http = HttpClient.newHttpClient();
    protected String city;

    @BeforeEach
    void baseSetUp() {
        RouteInfo route = new RouteInfo(new BigDecimal("3.200"), 9, "poly");
        when(mapsService.route(anyList())).thenReturn(route);
        when(mapsService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(route);
        city = "it-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO served_cities (slug, display_name) VALUES (?, ?)", city, city);
    }

    // ------------------------------------------------------------------------
    // usuarios
    // ------------------------------------------------------------------------

    protected User newUser(Role role) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@it.local");
        u.setName("IT " + role);
        u.setPhone("5574" + (long) (Math.random() * 1_000_000_000L));
        u.setRole(role);
        u.setCidade(city);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.saveAndFlush(u);
    }

    /** Motorista de carro aprovado, CNH valida e online — pronto para aceitar. */
    protected User newOperationalDriver() {
        return newDriver(VehicleType.CARRO, true);
    }

    /** Linha em drivers para um usuario novo; @MapsId exige o User gerenciado na mesma transacao. */
    protected User newDriver(VehicleType vehicleType, boolean operational) {
        User u = newUser(Role.MOTORISTA);
        tx.executeWithoutResult(status -> {
            Driver d = new Driver();
            d.setUser(userRepository.getReferenceById(u.getId()));
            d.setVehicleType(vehicleType);
            d.setVehiclePlate("ABC1D23");
            d.setApproved(operational);
            d.setOnline(operational);
            d.setCnhExpiresOn(LocalDate.now().plusYears(1));
            driverRepository.save(d);
        });
        return u;
    }

    protected String token(User u) {
        return jwtService.generate(u).token();
    }

    // ------------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------------

    protected URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    protected HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path));
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    protected HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest request) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String path, String token) {
        return send(request(path, token).GET().build());
    }

    protected HttpRequest postJson(String path, String token, String body) {
        return request(path, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    protected HttpRequest putJson(String path, String token, String body) {
        return request(path, token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    /** Abre um stream SSE de verdade; as linhas chegam numa fila lida por {@link SseStream#await}. */
    protected SseStream openSse(String path, String token) {
        SseStream sse = new SseStream();
        http.sendAsync(request(path, token).header("Accept", "text/event-stream").GET().build(),
                        HttpResponse.BodyHandlers.ofLines())
                .thenAcceptAsync(response -> {
                    sse.body = response.body();
                    sse.body.forEach(sse.lines::add);
                });
        return sse;
    }

    protected static final class SseStream implements AutoCloseable {
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private volatile Stream<String> body;

        /** Proxima linha {@code data:} que contem {@code fragment}; falha apos 10 s. */
        public String await(String fragment) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try {
                while (System.nanoTime() < deadline) {
                    String line = lines.poll(200, TimeUnit.MILLISECONDS);
                    if (line != null && line.startsWith("data:") && line.contains(fragment)) {
                        return line;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new AssertionError("SSE sem evento contendo " + fragment);
        }

        @Override
        public void close() {
            if (body != null) {
                body.close();
            }
        }
    }

    protected static JsonNode json(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException(response.body(), e);
        }
    }

    // ------------------------------------------------------------------------
    // corrida
    // ------------------------------------------------------------------------

    protected static final String RIDE_BODY = """
            {"latOrigem":-9.6300,"lngOrigem":-42.0800,"latDestino":-9.6400,"lngDestino":-42.0900}
            """;

    /** Passageiro pede e confirma (dinheiro): a corrida fica no mural. */
    protected String rideInMural(String passengerToken) {
        HttpResponse<String> created = send(postJson("/rides", passengerToken, RIDE_BODY));
        if (created.statusCode() != 201) {
            throw new IllegalStateException("POST /rides → " + created.statusCode() + " " + created.body());
        }
        String rideId = json(created).get("id").asText();
        HttpResponse<String> confirmed = send(postJson("/rides/" + rideId + "/confirm-payment", passengerToken,
                "{\"method\":\"DINHEIRO\"}"));
        if (confirmed.statusCode() != 200) {
            throw new IllegalStateException("confirm-payment → " + confirmed.statusCode() + " " + confirmed.body());
        }
        return rideId;
    }

    protected static List<Integer> statuses(List<CompletableFuture<HttpResponse<String>>> calls) {
        return calls.stream().map(CompletableFuture::join).map(HttpResponse::statusCode).sorted().toList();
    }
}
