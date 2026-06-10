package com.unimove.domain.ride.dto;

import java.util.UUID;

/**
 * Evento SSE "ride-removed" do stream do mural: a corrida saiu do mural.
 * reason: ACCEPTED | CANCELLED | EXPIRED.
 */
public record MuralRideRemovedEvent(UUID rideId, String reason) {}
