package com.unimove.domain.user.dto;

import com.unimove.domain.user.VehicleType;
import com.unimove.domain.user.document.DocumentationStatus;

import java.time.Instant;
import java.util.UUID;

/** Motorista aguardando aprovacao, com a situacao documental para o admin priorizar. */
public record PendingDriverItem(
        UUID userId,
        String name,
        String email,
        String phone,
        String cidade,
        VehicleType vehicleType,
        String vehiclePlate,
        Instant registeredAt,
        DocumentationStatus documentationStatus
) {}
