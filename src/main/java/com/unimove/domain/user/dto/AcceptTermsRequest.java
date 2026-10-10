package com.unimove.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptTermsRequest(
        @NotBlank @Size(max = 20) String version
) {}
