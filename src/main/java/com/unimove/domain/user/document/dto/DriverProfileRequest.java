package com.unimove.domain.user.document.dto;

import com.unimove.shared.util.Cpf;
import com.unimove.shared.util.VehiclePlate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Dados declarados do motorista (RF02). CPF aceita com ou sem pontuacao; a
 * placa segue o padrao antigo (ABC1234) ou Mercosul (ABC1D23).
 */
public record DriverProfileRequest(
        @NotBlank String cpf,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "CNH deve ter 11 dígitos.") String cnhNumber,
        @NotBlank @Pattern(regexp = "A|B|C|D|E|AB|AC|AD|AE", message = "Categoria de CNH inválida.") String cnhCategory,
        @NotNull LocalDate cnhExpiresOn,
        @NotBlank @Size(max = 40) String vehicleBrand,
        @NotBlank @Size(max = 60) String vehicleModel,
        @NotNull @Min(1990) @Max(2100) Integer vehicleYear,
        @NotBlank @Size(max = 30) String vehicleColor,
        @NotBlank
        @Pattern(regexp = VehiclePlate.PATTERN, message = VehiclePlate.MESSAGE)
        String vehiclePlate
) {
    @AssertTrue(message = "CPF inválido.")
    public boolean isCpfValid() {
        return cpf == null || Cpf.isValid(cpf);
    }
}
