package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.CancelledBy;
import com.unimove.domain.ride.Ride;
import com.unimove.domain.ride.RideStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento leve de transicao de estado da corrida, entregue via SSE
 * (ver regra 18). Carrega apenas o necessario pro cliente reagir; detalhes
 * (motorista, localizacao, preco) sao buscados via GET /rides/{id} quando
 * o cliente decide que vale a pena.
 *
 * {@code at} reflete o instante real da transicao (acceptedAt/startedAt/...),
 * nao o momento do envio — assim o snapshot de reconexao continua fiel.
 */
public record RideStatusEvent(
        UUID rideId,
        RideStatus status,
        Instant at,
        boolean terminal,
        CancelledBy cancelledBy,
        String cancelReason
) {
    public static RideStatusEvent from(Ride r) {
        return new RideStatusEvent(
                r.getId(),
                r.getStatus(),
                timestampFor(r),
                isTerminal(r.getStatus()),
                r.getCancelledBy(),
                r.getCancelReason()
        );
    }

    /** COMPLETED, CANCELLED e EXPIRED encerram a corrida — o stream fecha apos emiti-los. */
    public static boolean isTerminal(RideStatus status) {
        return switch (status) {
            case COMPLETED, CANCELLED, EXPIRED -> true;
            default -> false;
        };
    }

    /** Instante da transicao para o estado atual; cai em createdAt quando nao ha coluna dedicada. */
    private static Instant timestampFor(Ride r) {
        Instant ts = switch (r.getStatus()) {
            case PENDING_PAYMENT, AVAILABLE_IN_MURAL -> r.getCreatedAt();
            case DRIVER_EN_ROUTE -> r.getAcceptedAt();
            case IN_PROGRESS -> r.getStartedAt();
            case COMPLETED -> r.getCompletedAt();
            case CANCELLED -> r.getCancelledAt();
            case EXPIRED -> r.getExpiredAt();
        };
        return ts != null ? ts : Instant.now();
    }
}
