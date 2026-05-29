package com.unimove.domain.maps;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.photon")
public record PhotonProperties(
        @NotBlank String baseUrl
) {}
