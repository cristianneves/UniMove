package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.security.JwtService;
import com.unimove.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanDb() {
        driverRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerPassageiroCreatesUserAndIssuesToken() {
        RegisterRequest req = new RegisterRequest(
                "passageiro@example.com",
                "senha12345",
                "Maria Silva",
                "+5517999999999",
                Role.PASSAGEIRO,
                "São José do Rio Preto",
                null, null
        );

        AuthResponse resp = authService.register(req);

        assertThat(resp.token()).isNotBlank();
        assertThat(resp.role()).isEqualTo(Role.PASSAGEIRO);
        assertThat(resp.cidade()).isEqualTo("sao-jose-do-rio-preto");

        AuthenticatedUser parsed = jwtService.parse(resp.token());
        assertThat(parsed.userId()).isEqualTo(resp.userId());
        assertThat(parsed.email()).isEqualTo("passageiro@example.com");

        assertThat(userRepository.findByEmail("passageiro@example.com")).isPresent();
        assertThat(driverRepository.findByUserId(resp.userId())).isEmpty();
    }

    @Test
    void registerMotoristaCreatesDriverInPendingState() {
        RegisterRequest req = new RegisterRequest(
                "moto@example.com",
                "senha12345",
                "João Motorista",
                null,
                Role.MOTORISTA,
                "Campinas",
                VehicleType.MOTO,
                "abc1d23"
        );

        AuthResponse resp = authService.register(req);

        Driver driver = driverRepository.findByUserId(resp.userId()).orElseThrow();
        assertThat(driver.isApproved()).isFalse();
        assertThat(driver.isOnline()).isFalse();
        assertThat(driver.getVehicleType()).isEqualTo(VehicleType.MOTO);
        assertThat(driver.getVehiclePlate()).isEqualTo("ABC1D23");
    }

    @Test
    void registerDuplicateEmailThrowsEmailAlreadyUsed() {
        RegisterRequest req = new RegisterRequest(
                "dup@example.com", "senha12345", "Ana", null,
                Role.PASSAGEIRO, "Campinas", null, null
        );
        authService.register(req);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() {
        authService.register(new RegisterRequest(
                "login@example.com", "senha12345", "Ana", null,
                Role.PASSAGEIRO, "Campinas", null, null
        ));

        AuthResponse resp = authService.login(new LoginRequest("login@example.com", "senha12345"));

        assertThat(resp.token()).isNotBlank();
        assertThat(jwtService.parse(resp.token()).email()).isEqualTo("login@example.com");
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        authService.register(new RegisterRequest(
                "wrong@example.com", "senha12345", "Ana", null,
                Role.PASSAGEIRO, "Campinas", null, null
        ));

        assertThatThrownBy(() -> authService.login(new LoginRequest("wrong@example.com", "outraSenha")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailThrowsBadCredentials() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "qualquer1")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
