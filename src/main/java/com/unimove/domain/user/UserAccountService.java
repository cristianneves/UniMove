package com.unimove.domain.user;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.user.dto.AdminUserItem;
import com.unimove.domain.user.dto.PassengerPublicInfo;
import com.unimove.domain.user.dto.UserStatusResponse;
import com.unimove.shared.event.AccountAccessRevokedEvent;
import com.unimove.shared.security.AccountStatusChecker;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserAccountService implements UserDirectory, AccountStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository userRepository;
    private final AdminAuditLog auditLog;
    private final ApplicationEventPublisher events;

    public UserAccountService(UserRepository userRepository, AdminAuditLog auditLog,
                              ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.auditLog = auditLog;
        this.events = events;
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
    public AccountState stateOf(UUID userId, Instant tokenIssuedAt) {
        AccountSnapshot account = userRepository.findAccountSnapshot(userId).orElse(null);
        if (account == null || account.status() == UserStatus.DELETED) {
            return AccountState.GONE;
        }
        if (account.status() == UserStatus.SUSPENDED) {
            return AccountState.SUSPENDED;
        }
        // iat do JWT tem precisao de segundo: compara truncado, senao o token
        // emitido no mesmo segundo da troca (o novo) seria recusado.
        if (account.tokensValidAfter() != null && tokenIssuedAt != null
                && tokenIssuedAt.isBefore(account.tokensValidAfter().truncatedTo(ChronoUnit.SECONDS))) {
            return AccountState.REVOKED;
        }
        return account.mustChangePassword() ? AccountState.PASSWORD_CHANGE_REQUIRED : AccountState.ACTIVE;
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
        events.publishEvent(new AccountAccessRevokedEvent(targetUserId));
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
    public Page<AdminUserItem> search(String query, Role role, UserStatus status, String cidade, Pageable pageable) {
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String city = cidade == null || cidade.isBlank() ? null : CityNormalizer.normalize(cidade);
        return userRepository.search(q, role, status, city, pageable).map(AdminUserItem::from);
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
