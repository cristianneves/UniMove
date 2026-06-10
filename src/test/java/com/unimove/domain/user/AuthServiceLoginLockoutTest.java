package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes da integração do {@link AuthService#login} com o
 * {@link LoginAttemptService}: registro de falha/sucesso e curto-circuito
 * quando o e-mail está bloqueado.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginLockoutTest {

    @Mock UserRepository userRepository;
    @Mock DriverRepository driverRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock LoginAttemptService loginAttemptService;

    @InjectMocks AuthService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("p@example.com");
        user.setPasswordHash("hash");
        user.setName("Maria Silva");
        user.setRole(Role.PASSAGEIRO);
        user.setCidade("remanso");
    }

    @Test
    @DisplayName("login com e-mail desconhecido registra falha")
    void unknownEmailRecordsFailure() {
        when(userRepository.findByEmail("p@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("p@example.com", "senha")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).recordFailure("p@example.com");
    }

    @Test
    @DisplayName("login com senha incorreta registra falha")
    void wrongPasswordRecordsFailure() {
        when(userRepository.findByEmail("p@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("p@example.com", "errada")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).recordFailure("p@example.com");
    }

    @Test
    @DisplayName("login bem-sucedido zera o contador e emite o token")
    void successRecordsSuccess() {
        when(userRepository.findByEmail("p@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(jwtService.generate(user))
                .thenReturn(new JwtService.IssuedToken("jwt", Instant.now().plusSeconds(3600)));

        AuthResponse resp = service.login(new LoginRequest("p@example.com", "senha"));

        assertThat(resp.token()).isEqualTo("jwt");
        verify(loginAttemptService).recordSuccess("p@example.com");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    @DisplayName("e-mail bloqueado curto-circuita antes de consultar o banco")
    void lockedEmailShortCircuits() {
        doThrow(new TooManyLoginAttemptsException(15))
                .when(loginAttemptService).assertNotLocked("p@example.com");

        assertThatThrownBy(() -> service.login(new LoginRequest("p@example.com", "senha")))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        verify(userRepository, never()).findByEmail(anyString());
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    @DisplayName("e-mail é normalizado (trim + lowercase) antes das verificações")
    void emailIsNormalized() {
        when(userRepository.findByEmail("p@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("  P@Example.COM  ", "senha")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).assertNotLocked("p@example.com");
        verify(loginAttemptService).recordFailure("p@example.com");
    }

    @Test
    @DisplayName("usuário suspenso com senha correta zera o contador, mas não loga")
    void suspendedUserWithCorrectPasswordClearsCounter() {
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("p@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("p@example.com", "senha")))
                .isInstanceOf(UserSuspendedException.class);

        verify(loginAttemptService).recordSuccess("p@example.com");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }
}
