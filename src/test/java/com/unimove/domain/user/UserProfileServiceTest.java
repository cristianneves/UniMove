package com.unimove.domain.user;

import com.unimove.domain.user.dto.AdminResetPasswordResponse;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link UserProfileService} com Mockito.
 *
 * Cobertura: troca de senha (validação da senha atual) e reset pelo admin
 * (bloqueio para contas ADMIN, geração de senha temporária).
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserProfileService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private User user;
    private AuthenticatedUser auth;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setEmail("p@example.com");
        user.setPasswordHash("hash-antigo");
        user.setName("Maria Silva");
        user.setRole(Role.PASSAGEIRO);
        user.setCidade("remanso");

        auth = new AuthenticatedUser(USER_ID, "p@example.com", Role.PASSAGEIRO, "remanso");
    }

    @Test
    @DisplayName("changePassword troca o hash quando a senha atual confere")
    void changePasswordHappyPath() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senhaAtual", "hash-antigo")).thenReturn(true);
        when(passwordEncoder.encode("senhaNova123")).thenReturn("hash-novo");

        service.changePassword(auth, "senhaAtual", "senhaNova123");

        assertThat(user.getPasswordHash()).isEqualTo("hash-novo");
    }

    @Test
    @DisplayName("changePassword rejeita senha atual incorreta sem alterar o hash")
    void changePasswordWrongCurrent() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash-antigo")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(auth, "errada", "senhaNova123"))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hash-antigo");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("changePassword falha se o usuário do token não existe mais")
    void changePasswordUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(auth, "x", "senhaNova123"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("resetPassword gera senha temporária, persiste o hash e retorna a senha em claro")
    void resetPasswordHappyPath() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-temporario");

        AdminResetPasswordResponse resp = service.resetPassword(USER_ID, ADMIN_ID);

        assertThat(resp.userId()).isEqualTo(USER_ID);
        assertThat(resp.email()).isEqualTo("p@example.com");
        assertThat(resp.temporaryPassword()).hasSize(10);
        // Alfabeto sem caracteres ambíguos (0/O, 1/l/I)
        assertThat(resp.temporaryPassword()).matches("[A-HJ-NP-Za-hj-km-np-z2-9]{10}");
        assertThat(user.getPasswordHash()).isEqualTo("hash-temporario");
        verify(passwordEncoder).encode(resp.temporaryPassword());
    }

    @Test
    @DisplayName("resetPassword não permite resetar conta ADMIN")
    void resetPasswordAdminForbidden() {
        user.setRole(Role.ADMIN);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(USER_ID, ADMIN_ID))
                .isInstanceOf(CannotResetAdminPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hash-antigo");
        verify(passwordEncoder, never()).encode(anyString());
    }
}
