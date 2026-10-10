package com.unimove.domain.ride.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obrigatorio: vai para a corrida e para a trilha de auditoria. */
public record AdminCancelRideRequest(
        @NotBlank @Size(max = 500) String reason
) {}
