package com.unimove.domain.user;

import com.unimove.domain.city.CityCatalog;
import com.unimove.domain.city.CityNotServedException;
import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.domain.verification.PhoneVerificationRequiredException;
import com.unimove.domain.verification.PhoneVerificationService;
import com.unimove.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link AuthService#register}: a guarda contra auto-cadastro como
 * ADMIN e a exigência de telefone verificado no WhatsApp.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final String TOKEN = "token-verificado";
    /** Telefone como a Meta entrega: E.164 sem '+'. */
    private static final String VERIFIED_PHONE = "5574999990000";

    @Mock UserRepository userRepository;
    @Mock DriverRepository driverRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock PhoneVerificationService phoneVerificationService;
    @Mock CityCatalog cityCatalog;

    private AuthService service;

    @BeforeEach
    void setUp() {
        // Clock fixo para poder asseverar phoneVerifiedAt.
        service = new AuthService(userRepository, driverRepository, passwordEncoder, jwtService,
                loginAttemptService, phoneVerificationService, cityCatalog,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RegisterRequest request(Role role, VehicleType vehicleType, String plate) {
        return new RegisterRequest("novo@example.com", "senha12345", "  Maria Silva  ", TOKEN,
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
        verify(phoneVerificationService, never()).consumeVerifiedPhone(anyString());
    }

    @Test
    @DisplayName("passageiro é salvo com o telefone verificado e recebe o token")
    void registerPassageiroSavesUserAndIssuesToken() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("hash");
        when(phoneVerificationService.consumeVerifiedPhone(TOKEN)).thenReturn(VERIFIED_PHONE);
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
        assertThat(saved.getPhone()).isEqualTo(VERIFIED_PHONE);
        assertThat(saved.getPhoneVerifiedAt()).isEqualTo(NOW);

        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.role()).isEqualTo(Role.PASSAGEIRO);
        verify(driverRepository, never()).save(any());
        // A whitelist é consultada com o slug normalizado, não com o texto cru.
        verify(cityCatalog).assertServed("remanso");
    }

    @Test
    @DisplayName("motorista nasce pendente de aprovação")
    void registerMotoristaCreatesPendingDriver() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("hash");
        when(phoneVerificationService.consumeVerifiedPhone(TOKEN)).thenReturn(VERIFIED_PHONE);
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
    @DisplayName("e-mail já cadastrado devolve conflito sem consumir a verificação")
    void registerWithExistingEmailThrows() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request(Role.PASSAGEIRO, null, null)))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
        // Sem consumir o token, o usuário corrige o e-mail sem reverificar o telefone.
        verify(phoneVerificationService, never()).consumeVerifiedPhone(anyString());
    }

    @Test
    @DisplayName("cidade fora da lista de atendimento barra o cadastro sem gastar a verificação")
    void registerInCityNotServedThrows() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        doThrow(new CityNotServedException()).when(cityCatalog).assertServed("remanso");

        assertThatThrownBy(() -> service.register(request(Role.PASSAGEIRO, null, null)))
                .isInstanceOf(CityNotServedException.class);

        verify(userRepository, never()).save(any());
        // O desafio do WhatsApp sobrevive: o usuário corrige a cidade sem reverificar.
        verify(phoneVerificationService, never()).consumeVerifiedPhone(anyString());
    }

    @Test
    @DisplayName("token de verificação inválido impede o cadastro")
    void registerWithInvalidVerificationTokenThrows() {
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(phoneVerificationService.consumeVerifiedPhone(TOKEN))
                .thenThrow(new PhoneVerificationRequiredException());

        assertThatThrownBy(() -> service.register(request(Role.PASSAGEIRO, null, null)))
                .isInstanceOf(PhoneVerificationRequiredException.class);

        verify(userRepository, never()).save(any());
        verify(driverRepository, never()).save(any());
        verify(jwtService, never()).generate(any());
    }
}
