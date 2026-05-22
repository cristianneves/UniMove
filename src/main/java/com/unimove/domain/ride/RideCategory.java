package com.unimove.domain.ride;

import com.unimove.domain.user.VehicleType;

public enum RideCategory {
    MOTO,
    CARRO;

    public static RideCategory fromVehicleType(VehicleType vt) {
        return switch (vt) {
            case MOTO -> MOTO;
            case CARRO -> CARRO;
        };
    }
}
