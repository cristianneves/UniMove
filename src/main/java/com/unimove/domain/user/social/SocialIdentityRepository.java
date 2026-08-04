package com.unimove.domain.user.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentityEntity, UUID> {

    Optional<SocialIdentityEntity> findByProviderAndSubject(SocialProvider provider, String subject);

    List<SocialIdentityEntity> findByUserId(UUID userId);
}
