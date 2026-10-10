package com.unimove.domain.notification.dto;

import com.unimove.domain.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Campanha promocional. Segmento opcional: cidade (slug ou nome) e/ou papel;
 * sem segmento vai para todos os que deram opt-in.
 */
public record CreateCampaignRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 500) String body,
        @Size(max = 80) String cidade,
        Role role
) {
    @AssertTrue(message = "Campanha so pode mirar PASSAGEIRO ou MOTORISTA.")
    public boolean isRoleTargetable() {
        return role != Role.ADMIN;
    }
}
