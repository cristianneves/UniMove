package com.unimove.domain.user;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.user.dto.PassengerPublicInfo;
import com.unimove.domain.user.dto.UserStatusResponse;
import com.unimove.shared.security.AccountStatusChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserAccountService implements UserDirectory, AccountStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository userRepository;
    private final AdminAuditLog auditLog;

    public UserAccountService(UserRepository userRepository, AdminAuditLog auditLog) {
        this.userRepository = userRepository;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public Optional<PassengerPublicInfo> findPublicInfo(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new PassengerPublicInfo(firstName(u.getName())));
    }

    @Transactional(readOnly = true)
    public void requireActive(UUID userId) {
        UserStatus status = userRepository.findStatusById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (status != UserStatus.ACTIVE) {
            throw new UserSuspendedException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AccountState stateOf(UUID userId) {
        return userRepository.findStatusById(userId)
                .map(status -> switch (status) {
                    case ACTIVE -> AccountState.ACTIVE;
                    case SUSPENDED -> AccountState.SUSPENDED;
                    case DELETED -> AccountState.GONE;
                })
                .orElse(AccountState.GONE);
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
        auditLog.record(adminId, AuditAction.USER_SUSPENDED, "USER", targetUserId, reason);
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
        auditLog.record(adminId, AuditAction.USER_REACTIVATED, "USER", targetUserId, null);
        log.info("Usuário {} reativado pelo admin {}", targetUserId, adminId);
        return UserStatusResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCidade(String cidade) {
        return userRepository.countByCidade(cidade);
    }

    @Transactional(readOnly = true)
    public Page<UserStatusResponse> listSuspended(Pageable pageable) {
        return userRepository.findByStatus(UserStatus.SUSPENDED, pageable)
                .map(UserStatusResponse::from);
    }

    static String firstName(String fullName) {
        if (fullName == null) {
            return null;
        }
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int sp = trimmed.indexOf(' ');
        return sp < 0 ? trimmed : trimmed.substring(0, sp);
    }
}
