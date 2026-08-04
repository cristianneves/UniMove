package com.unimove.domain.user.dto;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.VehicleType;
import com.unimove.domain.user.social.SocialProvider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Completa o primeiro acesso via provedor social.
 *
 * <p>Nao ha {@code email} nem {@code password}: o e-mail vem do token do
 * provedor e a conta nasce sem senha. O {@code idToken} e reenviado e
 * revalidado — ele mesmo ja e a prova de identidade com validade propria
 * (~1h), entao nao precisamos de um "ticket de cadastro" com estado no
 * servidor.
 *
 * <p>{@code verificationToken} continua obrigatorio: o provedor social
 * substitui a senha, nunca a posse do telefone.
 */
public record SocialRegisterRequest(
        @NotNull SocialProvider provider,
        @NotBlank String idToken,
        @NotBlank @Size(max = 64) String verificationToken,
        @NotNull Role role,
        @NotBlank @Size(max = 80) String cidade,
        VehicleType vehicleType,
        @Size(max = 10) String vehiclePlate
) {
    @AssertTrue(message = "Cadastro permitido apenas para PASSAGEIRO ou MOTORISTA.")
    public boolean isRoleSelfRegisterable() {
        return role != Role.ADMIN;
    }

    @AssertTrue(message = "Motorista deve informar vehicleType e vehiclePlate; outros papéis não devem.")
    public boolean isVehicleConsistent() {
        boolean isDriver = role == Role.MOTORISTA;
        boolean hasVehicle = vehicleType != null
                && vehiclePlate != null
                && !vehiclePlate.isBlank();
        return isDriver == hasVehicle;
    }
}
