package com.unimove.domain.ride;

import com.unimove.domain.ride.DriverLocationPolicy.Decision;
import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverLocationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private final DriverLocationPolicy policy = new DriverLocationPolicy(DriverLocationProperties.defaults());

    @Test
    @DisplayName("primeira posicao valida e aceita")
    void firstFixAccepted() {
        assertThat(policy.evaluate(new Ride(), req("-9.60", "-42.10", NOW.minusSeconds(5), "10"), NOW))
                .isEqualTo(Decision.ACCEPT);
    }

    @Test
    @DisplayName("sem recordedAt o servidor usa o instante de chegada")
    void missingRecordedAtUsesNow() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(new BigDecimal("-9.6"), new BigDecimal("-42.1"));
        assertThat(policy.fixTime(req, NOW)).isEqualTo(NOW);
        assertThat(policy.evaluate(new Ride(), req, NOW)).isEqualTo(Decision.ACCEPT);
    }

    @Test
    @DisplayName("posicao velha (mais de 120s) → 422")
    void staleFixRejected() {
        assertThatThrownBy(() -> policy.evaluate(new Ride(), req("-9.60", "-42.10", NOW.minusSeconds(121), null), NOW))
                .isInstanceOf(InvalidDriverLocationException.class)
                .hasMessageContaining("desatualizada");
    }

    @Test
    @DisplayName("relogio do aparelho muito adiantado → 422")
    void futureFixRejected() {
        assertThatThrownBy(() -> policy.evaluate(new Ride(), req("-9.60", "-42.10", NOW.plusSeconds(31), null), NOW))
                .isInstanceOf(InvalidDriverLocationException.class)
                .hasMessageContaining("futuro");
    }

    @Test
    @DisplayName("precisao pior que 100 m → 422")
    void inaccurateFixRejected() {
        assertThatThrownBy(() -> policy.evaluate(new Ride(), req("-9.60", "-42.10", NOW, "150"), NOW))
                .isInstanceOf(InvalidDriverLocationException.class)
                .hasMessageContaining("Precisão");
    }

    @Test
    @DisplayName("salto de ~11 km em 10 s → 422")
    void impossibleJumpRejected() {
        Ride ride = rideAt("-9.60", "-42.10", NOW.minusSeconds(10));
        assertThatThrownBy(() -> policy.evaluate(ride, req("-9.70", "-42.10", NOW, null), NOW))
                .isInstanceOf(InvalidDriverLocationException.class)
                .hasMessageContaining("Salto");
    }

    @Test
    @DisplayName("deslocamento plausivel (~1,1 km em 60 s) e aceito")
    void plausibleMovementAccepted() {
        Ride ride = rideAt("-9.600", "-42.10", NOW.minusSeconds(60));
        assertThat(policy.evaluate(ride, req("-9.610", "-42.10", NOW, null), NOW)).isEqualTo(Decision.ACCEPT);
    }

    @Test
    @DisplayName("ruido pequeno com intervalo curto nao conta como salto")
    void smallJitterAccepted() {
        Ride ride = rideAt("-9.6000", "-42.10", NOW.minusSeconds(1));
        assertThat(policy.evaluate(ride, req("-9.6020", "-42.10", NOW, null), NOW)).isEqualTo(Decision.ACCEPT);
    }

    @Test
    @DisplayName("posicao igual ou anterior a atual → IGNORE_OUT_OF_ORDER")
    void outOfOrderIgnored() {
        Ride ride = rideAt("-9.60", "-42.10", NOW);
        assertThat(policy.evaluate(ride, req("-9.61", "-42.10", NOW.minusSeconds(3), null), NOW))
                .isEqualTo(Decision.IGNORE_OUT_OF_ORDER);
    }

    private static UpdateDriverLocationRequest req(String lat, String lng, Instant at, String accuracy) {
        return new UpdateDriverLocationRequest(new BigDecimal(lat), new BigDecimal(lng), at,
                accuracy == null ? null : new BigDecimal(accuracy));
    }

    private static Ride rideAt(String lat, String lng, Instant at) {
        Ride r = new Ride();
        r.setDriverCurrentLat(new BigDecimal(lat));
        r.setDriverCurrentLng(new BigDecimal(lng));
        r.setDriverLocationUpdatedAt(at);
        return r;
    }
}
