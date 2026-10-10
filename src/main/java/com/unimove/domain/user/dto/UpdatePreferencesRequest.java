package com.unimove.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePreferencesRequest(
        /** Receber ofertas/promocoes. Opt-in explicito; padrao e false. */
        @NotNull Boolean marketingOptIn
) {}
