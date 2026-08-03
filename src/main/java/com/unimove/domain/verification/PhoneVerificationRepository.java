package com.unimove.domain.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {

    Optional<PhoneVerification> findByCode(String code);

    Optional<PhoneVerification> findByToken(String token);

    boolean existsByCode(String code);

    @Modifying
    @Query("""
            UPDATE PhoneVerification v
               SET v.status = com.unimove.domain.verification.PhoneVerificationStatus.EXPIRED
             WHERE v.status = com.unimove.domain.verification.PhoneVerificationStatus.PENDING
               AND v.expiresAt < :now
            """)
    int markExpired(@Param("now") Instant now);

    @Modifying
    @Query("""
            DELETE FROM PhoneVerification v
             WHERE v.status <> com.unimove.domain.verification.PhoneVerificationStatus.PENDING
               AND v.expiresAt < :threshold
            """)
    int purgeTerminalOlderThan(@Param("threshold") Instant threshold);
}
