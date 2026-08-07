package com.unimove.domain.city.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O admin informa o nome legivel ("Remanso/BA"); o slug sai do
 * {@code CityNormalizer}, o mesmo usado no cadastro — sao sempre consistentes.
 */
public record OpenCityRequest(
        @NotBlank @Size(max = 120) String cidade
) {
}
