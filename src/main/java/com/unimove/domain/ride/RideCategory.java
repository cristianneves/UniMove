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

    /** Mapeamento inverso 1:1 — usado pelo surge para contar a oferta por tipo de veiculo. */
    public VehicleType toVehicleType() {
        return switch (this) {
            case MOTO -> VehicleType.MOTO;
            case CARRO -> VehicleType.CARRO;
        };
    }
}
