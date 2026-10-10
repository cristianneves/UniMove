package com.unimove.it;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class RideIT extends IntegrationTestSupport {

    private static final String DRIVER_POSITION = "{\"lat\":-9.6200,\"lng\":-42.0700}";

    @Test
    @DisplayName("dois motoristas aceitam a mesma corrida ao mesmo tempo → um 200 e um 409")
    void concurrentAcceptHasSingleWinner() {
        String passenger = token(newUser(Role.PASSAGEIRO));
        String rideId = rideInMural(passenger);
        String driverA = token(newOperationalDriver());
        String driverB = token(newOperationalDriver());

        List<Integer> result = statuses(List.of(
                sendAsync(postJson("/rides/" + rideId + "/accept", driverA, DRIVER_POSITION)),
                sendAsync(postJson("/rides/" + rideId + "/accept", driverB, DRIVER_POSITION))));

        assertThat(result).containsExactly(200, 409);
        String status = jdbc.queryForObject("SELECT status FROM rides WHERE id = ?::uuid", String.class, rideId);
        assertThat(status).isEqualTo("DRIVER_EN_ROUTE");
    }

    @Test
    @DisplayName("clique duplo no pedido → uma corrida criada e um 409")
    void doubleCreateIsBlocked() {
        User passenger = newUser(Role.PASSAGEIRO);
        String token = token(passenger);

        List<Integer> result = statuses(List.of(
                sendAsync(postJson("/rides", token, RIDE_BODY)),
                sendAsync(postJson("/rides", token, RIDE_BODY))));

        assertThat(result).containsExactly(201, 409);
        Integer rides = jdbc.queryForObject("SELECT count(*) FROM rides WHERE passageiro_id = ?",
                Integer.class, passenger.getId());
        assertThat(rides).isEqualTo(1);
    }

    @Test
    @DisplayName("historico ordenado por campo inexistente → 400, campo valido → 200")
    void invalidSortIsBadRequest() {
        String token = token(newUser(Role.PASSAGEIRO));

        assertThat(get("/rides/history?sort=naoExiste", token).statusCode()).isEqualTo(400);
        assertThat(get("/rides/history?sort=preco,desc", token).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("link de compartilhamento: dono recebe, publico abre, outro e barrado, encerrada → 410")
    void shareLink() {
        String owner = token(newUser(Role.PASSAGEIRO));
        String rideId = rideInMural(owner);

        HttpResponse<String> link = get("/rides/" + rideId + "/share-link", owner);
        assertThat(link.statusCode()).isEqualTo(200);
        String path = json(link).get("path").asText();
        assertThat(get(path, null).statusCode()).isEqualTo(200);

        String stranger = token(newUser(Role.PASSAGEIRO));
        assertThat(get("/rides/" + rideId + "/share-link", stranger).statusCode()).isEqualTo(403);

        assertThat(send(postJson("/rides/" + rideId + "/cancel", owner, "{}")).statusCode()).isEqualTo(200);
        assertThat(get("/rides/" + rideId + "/share-link", owner).statusCode()).isEqualTo(410);
        assertThat(get(path, null).statusCode()).isEqualTo(410);
    }
}
