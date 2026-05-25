package com.unimove.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByRideIdOrderBySeqAsc(UUID rideId);

    List<ChatMessage> findByRideIdAndSeqGreaterThanOrderBySeqAsc(UUID rideId, long seq);
}
