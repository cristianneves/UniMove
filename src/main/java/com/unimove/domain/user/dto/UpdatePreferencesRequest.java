package com.unimove.domain.user.dto;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RideCategory;
import jakarta.validation.constraints.NotNull;

public record UpdatePreferencesRequest(
        /** Receber ofertas/promocoes. Opt-in explicito; padrao e false. */
        @NotNull Boolean marketingOptIn,
        /** Passageiro: metodo pre-selecionado no pedido. Ausente/nulo = sem preferencia. */
        PaymentMethod preferredPaymentMethod,
        /** Passageiro: categoria pre-selecionada no pedido. Ausente/nulo = sem preferencia. */
        RideCategory preferredCategory
) {}
