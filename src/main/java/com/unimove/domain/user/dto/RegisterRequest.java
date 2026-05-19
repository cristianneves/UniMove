package com.unimove.domain.user.dto;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.VehicleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 20) String phone,
        @NotNull Role role,
        @NotBlank @Size(max = 80) String cidade,
        VehicleType vehicleType,
        @Size(max = 10) String vehiclePlate
) {
    @AssertTrue(message = "Motorista deve informar vehicleType e vehiclePlate; outros papéis não devem.")
    public boolean isVehicleConsistent() {
        boolean isDriver = role == Role.MOTORISTA;
        boolean hasVehicle = vehicleType != null
                && vehiclePlate != null
                && !vehiclePlate.isBlank();
        return isDriver == hasVehicle;
    }
}
