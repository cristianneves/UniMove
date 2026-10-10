package com.unimove.domain.user.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O motivo aparece para o motorista — escrever o que ele precisa corrigir. */
public record RejectDocumentRequest(
        @NotBlank @Size(max = 500) String reason
) {}
