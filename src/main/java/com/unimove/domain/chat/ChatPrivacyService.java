package com.unimove.domain.chat;

import com.unimove.domain.chat.dto.ChatMessageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class ChatPrivacyService implements ChatPrivacyPort {

    private final ChatMessageRepository repository;

    ChatPrivacyService(ChatMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> exportSentBy(UUID userId) {
        return repository.findBySenderIdOrderByCreatedAtAsc(userId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public int deleteSentBy(UUID userId) {
        return repository.deleteBySenderId(userId);
    }

    @Override
    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return repository.deleteCreatedBefore(cutoff);
    }
}
