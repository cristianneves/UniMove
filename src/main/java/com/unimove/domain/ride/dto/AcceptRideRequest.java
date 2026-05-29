package com.unimove.domain.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body OPCIONAL do POST /rides/{id}/accept. Quando o app do motorista envia
 * sua posicao atual, o backend calcula UMA vez (via OSRM) o ETA de chegada na
 * origem do passageiro ("motorista chega em ~5 min") e ja semeia a localizacao
 * do motorista na corrida — assim o passageiro ve posicao + distancia logo no
 * aceite, antes do primeiro PUT /driver-location.
 *
 * Calculo pontual no aceite, NAO no polling — respeita a regra 10 (GET de 5s
 * nunca chama OSRM). Sem body, o aceite funciona normalmente e o ETA fica nulo.
 * Quando presente, ambas as coordenadas sao obrigatorias.
 */
public record AcceptRideRequest(
        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal lat,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal lng
) {}
