package com.unimove.domain.user;

import com.unimove.domain.user.dto.UserStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository userRepository;

    public UserAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void requireActive(UUID userId) {
        UserStatus status = userRepository.findStatusById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (status != UserStatus.ACTIVE) {
            throw new UserSuspendedException();
        }
    }

    @Transactional
    public UserStatusResponse suspend(UUID targetUserId, UUID adminId, String reason) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotSuspendAdminException();
        }
        user.setStatus(UserStatus.SUSPENDED);
        user.setSuspendedAt(Instant.now());
        user.setSuspendedReason(reason);
        user.setSuspendedByAdminId(adminId);
        log.warn("Usuário {} suspenso pelo admin {} (motivo: {})", targetUserId, adminId, reason);
        return UserStatusResponse.from(user);
    }

    @Transactional
    public UserStatusResponse reactivate(UUID targetUserId, UUID adminId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(UserNotFoundException::new);
        user.setStatus(UserStatus.ACTIVE);
        user.setSuspendedAt(null);
        user.setSuspendedReason(null);
        user.setSuspendedByAdminId(null);
        log.info("Usuário {} reativado pelo admin {}", targetUserId, adminId);
        return UserStatusResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserStatusResponse> listSuspended(Pageable pageable) {
        return userRepository.findByStatus(UserStatus.SUSPENDED, pageable)
                .map(UserStatusResponse::from);
    }
}
