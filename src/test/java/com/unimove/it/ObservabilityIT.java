package com.unimove.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.unimove.domain.user.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RNF06: o que o deploy, o monitor externo e o scripts/smoke.sh leem sem token. */
@PostgresIntegrationTest
class ObservabilityIT extends IntegrationTestSupport {

    @Test
    void healthAndProbesArePublicAndUp() {
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            var response = get(path, null);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(json(response).get("status").asText()).as(path).isEqualTo("UP");
        }
    }

    @Test
    void healthDoesNotLeakDetails() {
        assertThat(json(get("/actuator/health", null)).has("components")).isFalse();
    }

    @Test
    void infoExposesBuildVersionAndCommit() {
        JsonNode build = json(get("/actuator/info", null)).get("build");
        assertThat(build.get("version").asText()).isNotBlank();
        assertThat(build.get("commit").asText()).isNotBlank();
    }

    @Test
    void otherActuatorEndpointsStayClosed() {
        assertThat(get("/actuator/env", null).statusCode()).isIn(401, 404);
        assertThat(get("/actuator/env", token(newUser(Role.ADMIN))).statusCode()).isEqualTo(404);
    }
}
