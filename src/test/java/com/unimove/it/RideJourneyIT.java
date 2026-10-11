package com.unimove.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.domain.user.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jornada completa pela API, como os aparelhos fazem (cards INTEGRACAO,
 * PASSAGEIRO e QA): passageiro × dois motoristas × admin, com SSE de status
 * e chat abertos de verdade. Pagamento em dinheiro, o unico do piloto.
 */
@PostgresIntegrationTest
class RideJourneyIT extends IntegrationTestSupport {

    private static final String POSITION = "{\"lat\":-9.6200,\"lng\":-42.0700}";

    @Test
    @DisplayName("estimar → pedir → mural → disputa → GPS → chat → iniciar → concluir → dinheiro → avaliar → historico → admin")
    void fullJourney() {
        User passengerUser = newUser(Role.PASSAGEIRO);
        String passenger = token(passengerUser);
        String driverA = token(newOperationalDriver());
        String driverB = token(newOperationalDriver());

        HttpResponse<String> estimate = send(postJson("/rides/estimate", passenger, RIDE_BODY));
        assertThat(estimate.statusCode()).isEqualTo(200);
        assertThat(json(estimate).get("preco").decimalValue()).isPositive();
        assertThat(json(estimate).get("options")).isNotEmpty();

        String rideId = rideInMural(passenger);
        assertThat(json(get("/rides/" + rideId, passenger)).get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(get("/rides/mural", driverA).body()).contains(rideId);
        assertThat(get("/rides/mural", token(newDriver(VehicleType.CARRO, false))).statusCode()).isBetween(400, 499);

        try (SseStream status = openSse("/rides/" + rideId + "/status-stream", passenger)) {
            status.await("AVAILABLE_IN_MURAL");
            assertThat(send(postJson("/chat/rides/" + rideId + "/messages", passenger, "{\"body\":\"oi\"}"))
                    .statusCode()).as("chat antes do aceite").isEqualTo(403);

            List<HttpResponse<String>> accepts = List.of(
                    sendAsync(postJson("/rides/" + rideId + "/accept", driverA, POSITION)),
                    sendAsync(postJson("/rides/" + rideId + "/accept", driverB, POSITION)))
                    .stream().map(CompletableFuture::join).toList();
            assertThat(accepts).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
            String winner = accepts.get(0).statusCode() == 200 ? driverA : driverB;
            String loser = winner.equals(driverA) ? driverB : driverA;
            status.await("DRIVER_EN_ROUTE");
            assertThat(get("/rides/mural", loser).body()).doesNotContain(rideId);
            assertThat(get("/rides/" + rideId, loser).statusCode()).isEqualTo(403);

            assertThat(send(putJson("/rides/" + rideId + "/driver-location", winner,
                    "{\"lat\":-9.6250,\"lng\":-42.0750}")).statusCode()).as("750 m em milissegundos").isEqualTo(422);
            assertThat(send(putJson("/rides/" + rideId + "/driver-location", winner,
                    "{\"lat\":-9.6230,\"lng\":-42.0720,\"accuracyM\":12}")).statusCode()).isEqualTo(200);
            JsonNode tracked = json(get("/rides/" + rideId, passenger));
            assertThat(tracked.get("driverCurrentLat").decimalValue()).isEqualByComparingTo("-9.6230");
            assertThat(send(putJson("/rides/" + rideId + "/driver-location", loser, POSITION)).statusCode()).isEqualTo(403);

            try (SseStream chat = openSse("/chat/rides/" + rideId + "/stream", winner)) {
                assertThat(send(postJson("/chat/rides/" + rideId + "/messages", passenger,
                        "{\"body\":\"Estou no portao azul\"}")).statusCode()).isEqualTo(200);
                chat.await("Estou no portao azul");
            }
            assertThat(get("/chat/rides/" + rideId + "/messages", winner).body()).contains("Estou no portao azul");
            assertThat(get("/chat/rides/" + rideId + "/messages", loser).statusCode()).isEqualTo(403);
            assertThat(get("/chat/rides/" + rideId + "/messages", token(newUser(Role.PASSAGEIRO))).statusCode())
                    .isEqualTo(403);

            assertThat(send(postJson("/rides/" + rideId + "/rating", passenger, "{\"score\":5}")).statusCode())
                    .as("avaliar antes de concluir").isBetween(400, 499);
        }
    }

    @Test
    @DisplayName("corrida concluida: SSE fecha, chat trava, dinheiro registrado uma vez, avaliacao unica, historico e admin")
    void completedRide() {
        User passengerUser = newUser(Role.PASSAGEIRO);
        String passenger = token(passengerUser);
        String driver = token(newOperationalDriver());
        String rideId = rideInMural(passenger);
        assertThat(send(postJson("/rides/" + rideId + "/accept", driver, POSITION)).statusCode()).isEqualTo(200);

        try (SseStream status = openSse("/rides/" + rideId + "/status-stream", passenger)) {
            status.await("DRIVER_EN_ROUTE");
            assertThat(send(postJson("/rides/" + rideId + "/start", driver, "{}")).statusCode()).isEqualTo(200);
            status.await("IN_PROGRESS");
            assertThat(send(postJson("/rides/" + rideId + "/cancel", passenger, "{}")).statusCode())
                    .as("passageiro nao cancela em andamento").isEqualTo(409);
            HttpResponse<String> completed = send(postJson("/rides/" + rideId + "/complete", driver, "{}"));
            assertThat(completed.statusCode()).isEqualTo(200);
            status.await("COMPLETED");
            assertThat(json(completed).get("driverCurrentLat").isNull()).as("posicao apagada ao concluir").isTrue();
        }

        assertThat(send(postJson("/chat/rides/" + rideId + "/messages", passenger, "{\"body\":\"oi\"}")).statusCode())
                .isEqualTo(403);

        String price = json(get("/rides/" + rideId, passenger)).get("preco").asText();
        HttpResponse<String> cash = send(postJson("/rides/" + rideId + "/cash-payment", driver,
                "{\"receivedAmount\":" + price + "}"));
        assertThat(cash.statusCode()).isEqualTo(200);
        assertThat(json(cash).get("paymentStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(send(postJson("/rides/" + rideId + "/cash-payment", driver,
                "{\"receivedAmount\":" + price + "}")).statusCode()).isEqualTo(409);

        HttpResponse<String> rated = send(postJson("/rides/" + rideId + "/rating", passenger,
                "{\"score\":5,\"comment\":\"otimo\"}"));
        assertThat(rated.statusCode()).isEqualTo(200);
        assertThat(json(rated).get("rateeNewRatingCount").asInt()).isEqualTo(1);
        assertThat(send(postJson("/rides/" + rideId + "/rating", passenger, "{\"score\":4}")).statusCode())
                .isEqualTo(409);
        assertThat(send(postJson("/rides/" + rideId + "/rating", driver, "{\"score\":4}")).statusCode())
                .isEqualTo(200);

        JsonNode history = json(get("/rides/history", passenger)).get("content");
        assertThat(history).anySatisfy(item -> {
            assertThat(item.get("id").asText()).isEqualTo(rideId);
            assertThat(item.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(item.get("paymentStatus").asText()).isEqualTo("CONFIRMED");
        });
        assertThat(get("/rides/history", driver).body()).contains(rideId);
        assertThat(get("/rides/history", token(newUser(Role.PASSAGEIRO))).body()).doesNotContain(rideId);

        String admin = token(newUser(Role.ADMIN));
        assertThat(get("/admin/rides/" + rideId, admin).statusCode()).isEqualTo(200);
        assertThat(get("/admin/rides/" + rideId, passenger).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("cancelamento: motorista sem motivo → 400, com motivo → 200; admin cancela com motivo e SSE avisa")
    void cancellationRules() {
        String passenger = token(newUser(Role.PASSAGEIRO));
        String driver = token(newOperationalDriver());
        String rideId = rideInMural(passenger);
        send(postJson("/rides/" + rideId + "/accept", driver, POSITION));

        assertThat(send(postJson("/rides/" + rideId + "/cancel", driver, "{}")).statusCode()).isEqualTo(400);
        HttpResponse<String> byDriver = send(postJson("/rides/" + rideId + "/cancel", driver,
                "{\"reason\":\"pneu furado\"}"));
        assertThat(byDriver.statusCode()).isEqualTo(200);
        assertThat(json(byDriver).get("cancelledBy").asText()).isEqualTo("MOTORISTA");

        String otherPassenger = token(newUser(Role.PASSAGEIRO));
        String other = rideInMural(otherPassenger);
        String admin = token(newUser(Role.ADMIN));
        assertThat(send(postJson("/admin/rides/" + other + "/cancel", admin, "{}")).statusCode()).isEqualTo(400);
        try (SseStream status = openSse("/rides/" + other + "/status-stream", otherPassenger)) {
            status.await("AVAILABLE_IN_MURAL");
            HttpResponse<String> byAdmin = send(postJson("/admin/rides/" + other + "/cancel", admin,
                    "{\"reason\":\"pedido duplicado\"}"));
            assertThat(byAdmin.statusCode()).isEqualTo(200);
            assertThat(json(byAdmin).get("cancelledBy").asText()).isEqualTo("ADMIN");
            assertThat(status.await("CANCELLED")).contains("ADMIN");
        }
        assertThat(send(postJson("/admin/rides/" + other + "/cancel", admin,
                "{\"reason\":\"de novo\"}")).statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("piloto so dinheiro: Pix → 409 e a corrida segue pendente; dinheiro publica no mural como PENDING")
    void pilotIsCashOnly() {
        String passenger = token(newUser(Role.PASSAGEIRO));
        String rideId = json(send(postJson("/rides", passenger, RIDE_BODY))).get("id").asText();

        HttpResponse<String> pix = send(postJson("/rides/" + rideId + "/confirm-payment", passenger, "{\"method\":\"PIX\"}"));
        assertThat(pix.statusCode()).isEqualTo(409);
        assertThat(json(pix).get("message").asText()).contains("dinheiro");
        assertThat(json(get("/rides/" + rideId, passenger)).get("status").asText()).isEqualTo("PENDING_PAYMENT");

        JsonNode cash = json(send(postJson("/rides/" + rideId + "/confirm-payment", passenger,
                "{\"method\":\"DINHEIRO\",\"changeFor\":100}")));
        assertThat(cash.get("status").asText()).isEqualTo("AVAILABLE_IN_MURAL");
        assertThat(cash.get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(cash.get("pixPayload").isNull()).isTrue();
    }
}
