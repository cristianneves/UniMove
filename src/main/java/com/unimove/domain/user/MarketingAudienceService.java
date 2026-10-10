package com.unimove.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class MarketingAudienceService implements MarketingAudience {

    private final UserRepository userRepository;

    MarketingAudienceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> optedInRecipients(String cidade, Role role) {
        return userRepository.findMarketingAudience(cidade, role);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOptedIn(String cidade, Role role) {
        return userRepository.countMarketingAudience(cidade, role);
    }
}
