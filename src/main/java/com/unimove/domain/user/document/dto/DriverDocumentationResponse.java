package com.unimove.domain.user.document.dto;

import com.unimove.domain.user.Driver;
import com.unimove.domain.user.VehicleType;
import com.unimove.domain.user.document.DocumentationStatus;
import com.unimove.shared.util.Cnh;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Situacao documental do motorista. {@code current} tem o envio vigente de
 * cada tipo; {@code history} todos os envios (inclusive reprovados), para o
 * admin ver o que ja foi analisado. {@code pending} lista em texto o que
 * falta para a aprovacao.
 */
public record DriverDocumentationResponse(
        UUID driverId,
        DocumentationStatus status,
        boolean approved,
        Profile profile,
        List<DriverDocumentItem> current,
        List<DriverDocumentItem> history,
        List<String> pending
) {
    public record Profile(
            String cpf,
            String cnhNumber,
            /** Sinal para o admin: digito verificador da CNH confere? Nulo sem CNH informada. */
            Boolean cnhCheckDigitValid,
            String cnhCategory,
            LocalDate cnhExpiresOn,
            VehicleType vehicleType,
            String vehiclePlate,
            String vehicleBrand,
            String vehicleModel,
            Short vehicleYear,
            String vehicleColor
    ) {
        public static Profile from(Driver d) {
            return new Profile(d.getCpf(), d.getCnhNumber(),
                    d.getCnhNumber() == null ? null : Cnh.hasValidCheckDigits(d.getCnhNumber()),
                    d.getCnhCategory(), d.getCnhExpiresOn(),
                    d.getVehicleType(), d.getVehiclePlate(), d.getVehicleBrand(), d.getVehicleModel(),
                    d.getVehicleYear(), d.getVehicleColor());
        }
    }
}
