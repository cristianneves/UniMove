package com.unimove.domain.user;

import com.unimove.domain.verification.PhoneRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador da porta {@link PhoneRegistry}: permite a verificacao recusar cedo
 * um numero que ja tem conta, sem que {@code domain.verification} enxergue o
 * {@link UserRepository}.
 */
@Component
class UserPhoneRegistry implements PhoneRegistry {

    private final UserRepository userRepository;

    UserPhoneRegistry(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPhoneRegistered(String phoneE164) {
        return phoneE164 != null && userRepository.existsByPhone(phoneE164);
    }
}
