package com.unimove.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank
        @Size(min = 1, max = 1000)
        String body
) {}
