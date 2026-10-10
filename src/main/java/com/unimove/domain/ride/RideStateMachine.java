package com.unimove.domain.ride;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RideStateMachine {

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(RideStatus.class);
        ALLOWED.put(RideStatus.PENDING_PAYMENT,
                EnumSet.of(RideStatus.AVAILABLE_IN_MURAL, RideStatus.CANCELLED));
        ALLOWED.put(RideStatus.AVAILABLE_IN_MURAL,
                EnumSet.of(RideStatus.DRIVER_EN_ROUTE, RideStatus.CANCELLED, RideStatus.EXPIRED));
        ALLOWED.put(RideStatus.DRIVER_EN_ROUTE,
                EnumSet.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED));
        // Cancelar em andamento: so motorista (com motivo) ou admin — a regra
        // de quem pode fica no RideService; aqui so a transicao.
        ALLOWED.put(RideStatus.IN_PROGRESS,
                EnumSet.of(RideStatus.COMPLETED, RideStatus.CANCELLED));
        ALLOWED.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        ALLOWED.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));
        ALLOWED.put(RideStatus.EXPIRED, EnumSet.noneOf(RideStatus.class));
    }

    private RideStateMachine() {}

    public static void assertCanTransition(RideStatus from, RideStatus to) {
        if (!ALLOWED.getOrDefault(from, EnumSet.noneOf(RideStatus.class)).contains(to)) {
            throw new IllegalRideTransitionException(from, to);
        }
    }
}
