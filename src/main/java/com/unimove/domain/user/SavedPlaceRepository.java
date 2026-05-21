package com.unimove.domain.user;

import com.unimove.domain.user.dto.SavedPlaceResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {

    boolean existsByUserIdAndLabel(UUID userId, String label);

    Optional<SavedPlace> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT new com.unimove.domain.user.dto.SavedPlaceResponse(
                p.id, p.label, p.address, p.lat, p.lng, p.createdAt
            )
            FROM SavedPlace p
            WHERE p.userId = :userId
            ORDER BY p.createdAt ASC
            """)
    List<SavedPlaceResponse> findAllByUser(@Param("userId") UUID userId);
}
