package com.unimove.domain.user;

import com.unimove.shared.security.AccountStatusChecker.AccountState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserAccountService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("stateOf: ACTIVE → ACTIVE")
    void activeIsActive() {
        when(userRepository.findStatusById(userId)).thenReturn(Optional.of(UserStatus.ACTIVE));
        assertThat(service.stateOf(userId)).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("stateOf: SUSPENDED → SUSPENDED")
    void suspendedIsSuspended() {
        when(userRepository.findStatusById(userId)).thenReturn(Optional.of(UserStatus.SUSPENDED));
        assertThat(service.stateOf(userId)).isEqualTo(AccountState.SUSPENDED);
    }

    @Test
    @DisplayName("stateOf: usuario inexistente → GONE")
    void missingIsGone() {
        when(userRepository.findStatusById(userId)).thenReturn(Optional.empty());
        assertThat(service.stateOf(userId)).isEqualTo(AccountState.GONE);
    }
}
