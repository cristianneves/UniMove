package com.unimove.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByRideIdOrderBySeqAsc(UUID rideId);

    List<ChatMessage> findByRideIdAndSeqGreaterThanOrderBySeqAsc(UUID rideId, long seq);

    List<ChatMessage> findBySenderIdOrderByCreatedAtAsc(UUID senderId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.senderId = :senderId")
    int deleteBySenderId(@Param("senderId") UUID senderId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
