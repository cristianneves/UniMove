package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.shared.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link AuthService#register}, com foco na guarda que impede
 * auto-cadastro como ADMIN (escalação de privilégio via body da requisição).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock UserRepository userRepository;
    @Mock DriverRepository driverRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock LoginAttemptService loginAttemptService;

    @InjectMocks AuthService service;

    private static RegisterRequest request(Role role, VehicleType vehicleType, String plate) {
        return new RegisterRequest("novo@example.com", "senha12345", "  Maria Silva  ", "77999990000",
                role, "Remanso", vehicleType, plate);
    }

    @Test
    @DisplayName("cadastro como ADMIN é bloqueado antes de qualquer escrita")
    void registerWithAdminRoleThrows() {
        assertThatThrownBy(() -> service.register(request(Role.ADMIN, null, null)))
                .isInstanceOf(RoleNotSelfAssignableException.class);

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
        verify(driverRepository, never()).save(any());
        verify(jwtService, never()).generate(any());
    }

    @Test
    @DisplayName("passageiro é salvo, normalizado e recebe o token")
    void registerPassageiroSavesUserAndIssuesToken() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("hash");
        when(jwtService.generate(any()))
                .thenReturn(new JwtService.IssuedToken("jwt", Instant.now().plusSeconds(3600)));

        AuthResponse resp = service.register(request(Role.PASSAGEIRO, null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.PASSAGEIRO);
        assertThat(saved.getName()).isEqualTo("Maria Silva");
        assertThat(saved.getPasswordHash()).isEqualTo("hash");
        assertThat(saved.getCidade()).isEqualTo("remanso");

        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.role()).isEqualTo(Role.PASSAGEIRO);
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("motorista nasce pendente de aprovação")
    void registerMotoristaCreatesPendingDriver() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("hash");
        when(jwtService.generate(any()))
                .thenReturn(new JwtService.IssuedToken("jwt", Instant.now().plusSeconds(3600)));

        service.register(request(Role.MOTORISTA, VehicleType.MOTO, " abc1d23 "));

        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository).save(captor.capture());
        Driver driver = captor.getValue();
        assertThat(driver.isApproved()).isFalse();
        assertThat(driver.isOnline()).isFalse();
        assertThat(driver.getVehicleType()).isEqualTo(VehicleType.MOTO);
        assertThat(driver.getVehiclePlate()).isEqualTo("ABC1D23");
    }

    @Test
    @DisplayName("e-mail já cadastrado devolve conflito")
    void registerWithExistingEmailThrows() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request(Role.PASSAGEIRO, null, null)))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
    }
}
