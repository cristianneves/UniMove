package com.unimove.domain.chat;

import com.unimove.domain.chat.dto.ChatMessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Direitos do titular sobre as mensagens de chat que ele enviou (LGPD). */
public interface ChatPrivacyPort {

    List<ChatMessageResponse> exportSentBy(UUID userId);

    int deleteSentBy(UUID userId);

    /** Retencao: apaga mensagens anteriores ao corte. */
    int deleteOlderThan(Instant cutoff);
}
