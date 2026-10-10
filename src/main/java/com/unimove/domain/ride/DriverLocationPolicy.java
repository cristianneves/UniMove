package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import com.unimove.shared.util.Haversine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Decide se uma posicao do motorista entra no tracking. Nunca fabrica
 * coordenada: ou aceita a do aparelho, ou ignora (fora de ordem), ou recusa
 * com o motivo para o app avisar o motorista.
 */
@Component
public class DriverLocationPolicy {

    public enum Decision { ACCEPT, IGNORE_OUT_OF_ORDER }

    private final DriverLocationProperties props;

    public DriverLocationPolicy(DriverLocationProperties props) {
        this.props = props;
    }

    /** Instante da posicao: o do GPS quando o app manda, senao o de chegada. */
    public Instant fixTime(UpdateDriverLocationRequest req, Instant now) {
        return req.recordedAt() != null ? req.recordedAt() : now;
    }

    public Decision evaluate(Ride ride, UpdateDriverLocationRequest req, Instant now) {
        Instant fixAt = fixTime(req, now);
        if (fixAt.isBefore(now.minusSeconds(props.maxAgeSeconds()))) {
            throw new InvalidDriverLocationException(
                    "Posição desatualizada (mais de " + props.maxAgeSeconds() + "s). Aguarde um novo sinal de GPS.");
        }
        if (fixAt.isAfter(now.plusSeconds(props.maxFutureSkewSeconds()))) {
            throw new InvalidDriverLocationException(
                    "Horário da posição no futuro. Verifique a data e hora do aparelho.");
        }
        if (req.accuracyM() != null && req.accuracyM().doubleValue() > props.maxAccuracyM()) {
            throw new InvalidDriverLocationException(
                    "Precisão do GPS insuficiente (" + req.accuracyM().intValue() + " m). Aguarde um sinal melhor.");
        }

        Instant prevAt = ride.getDriverLocationUpdatedAt();
        if (prevAt == null || ride.getDriverCurrentLat() == null || ride.getDriverCurrentLng() == null) {
            return Decision.ACCEPT;
        }
        // Rede instavel entrega fora de ordem: a posicao mais velha nao pode
        // sobrescrever a mais nova. 200 sem efeito, sem erro para o app.
        if (!fixAt.isAfter(prevAt)) {
            return Decision.IGNORE_OUT_OF_ORDER;
        }
        BigDecimal km = Haversine.distanceKm(ride.getDriverCurrentLat(), ride.getDriverCurrentLng(),
                req.lat(), req.lng());
        if (km.doubleValue() >= props.minJumpKm()) {
            double hours = Duration.between(prevAt, fixAt).toMillis() / 3_600_000.0;
            if (km.doubleValue() / hours > props.maxSpeedKmh()) {
                throw new InvalidDriverLocationException(
                        "Salto de posição incompatível com o deslocamento. Posição ignorada.");
            }
        }
        return Decision.ACCEPT;
    }
}
