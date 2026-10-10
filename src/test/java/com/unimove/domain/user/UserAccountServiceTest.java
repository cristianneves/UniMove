package com.unimove.domain.user;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.shared.event.AccountAccessRevokedEvent;
import com.unimove.shared.security.AccountStatusChecker.AccountState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock AdminAuditLog auditLog;
    @Mock ApplicationEventPublisher events;
    @InjectMocks UserAccountService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("suspend grava status, motivo e trilha de auditoria")
    void suspendIsAudited() {
        UUID adminId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setRole(Role.PASSAGEIRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.suspend(userId, adminId, "fraude");

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(auditLog).record(adminId, AuditAction.USER_SUSPENDED, "USER", userId, "fraude");
        verify(events).publishEvent(new AccountAccessRevokedEvent(userId));
    }

    @Test
    @DisplayName("suspend de ADMIN e recusado e nao audita nada")
    void suspendAdminRefused() {
        User admin = new User();
        admin.setId(userId);
        admin.setRole(Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.suspend(userId, UUID.randomUUID(), "x"))
                .isInstanceOf(CannotSuspendAdminException.class);
        verifyNoInteractions(auditLog);
    }

    @Test
    @DisplayName("reactivate limpa a suspensao e audita")
    void reactivateIsAudited() {
        UUID adminId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.reactivate(userId, adminId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(auditLog).record(adminId, AuditAction.USER_REACTIVATED, "USER", userId, null);
    }

    @Test
    @DisplayName("busca do admin: texto vira LIKE minusculo e cidade e normalizada; vazio = sem filtro")
    void searchBuildsFilters() {
        org.springframework.data.domain.Pageable page = org.springframework.data.domain.Pageable.unpaged();
        when(userRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search("  Maria ", Role.MOTORISTA, null, "Remanso/BA", page);
        verify(userRepository).search("%maria%", Role.MOTORISTA, null, "remanso-ba", page);

        service.search(" ", null, UserStatus.SUSPENDED, "", page);
        verify(userRepository).search(null, null, UserStatus.SUSPENDED, null, page);
    }

    @Test
    @DisplayName("stateOf: ACTIVE → ACTIVE")
    void activeIsActive() {
        snapshot(UserStatus.ACTIVE, null, false);
        assertThat(service.stateOf(userId, Instant.now())).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("stateOf: SUSPENDED → SUSPENDED")
    void suspendedIsSuspended() {
        snapshot(UserStatus.SUSPENDED, null, false);
        assertThat(service.stateOf(userId, Instant.now())).isEqualTo(AccountState.SUSPENDED);
    }

    @Test
    @DisplayName("stateOf: DELETED → GONE (token de conta excluida nao autentica)")
    void deletedIsGone() {
        snapshot(UserStatus.DELETED, null, false);
        assertThat(service.stateOf(userId, Instant.now())).isEqualTo(AccountState.GONE);
    }

    @Test
    @DisplayName("stateOf: usuario inexistente → GONE")
    void missingIsGone() {
        when(userRepository.findAccountSnapshot(userId)).thenReturn(Optional.empty());
        assertThat(service.stateOf(userId, Instant.now())).isEqualTo(AccountState.GONE);
    }

    @Test
    @DisplayName("stateOf: token emitido antes da troca de senha → REVOKED")
    void tokenBeforePasswordChangeIsRevoked() {
        Instant changed = Instant.parse("2026-10-10T12:00:00.700Z");
        snapshot(UserStatus.ACTIVE, changed, false);

        assertThat(service.stateOf(userId, Instant.parse("2026-10-10T11:59:59Z"))).isEqualTo(AccountState.REVOKED);
        // iat tem precisao de segundo: o token novo, emitido no mesmo segundo, segue valido.
        assertThat(service.stateOf(userId, Instant.parse("2026-10-10T12:00:00Z"))).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("stateOf: senha temporaria → PASSWORD_CHANGE_REQUIRED")
    void temporaryPassword() {
        snapshot(UserStatus.ACTIVE, null, true);
        assertThat(service.stateOf(userId, Instant.now())).isEqualTo(AccountState.PASSWORD_CHANGE_REQUIRED);
    }

    private void snapshot(UserStatus status, Instant validAfter, boolean mustChange) {
        when(userRepository.findAccountSnapshot(userId))
                .thenReturn(Optional.of(new AccountSnapshot(status, validAfter, mustChange)));
    }
}
