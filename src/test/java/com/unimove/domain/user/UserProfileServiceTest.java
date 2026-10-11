package com.unimove.domain.user;

import com.unimove.domain.city.CityCatalog;
import com.unimove.domain.city.CityNotServedException;
import com.unimove.domain.user.dto.AdminResetPasswordResponse;
import com.unimove.domain.user.dto.UpdateProfileRequest;
import com.unimove.domain.user.dto.UpdateProfileResponse;
import com.unimove.domain.user.dto.UserProfileResponse;
import com.unimove.domain.verification.PhoneVerificationRequiredException;
import com.unimove.domain.verification.PhoneVerificationService;
import com.unimove.shared.event.AccountAccessRevokedEvent;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link UserProfileService} com Mockito.
 *
 * Cobertura: troca de senha (validação da senha atual), reset pelo admin
 * (bloqueio para contas ADMIN, geração de senha temporária) e perfil
 * (bloco de veículo para motorista, reemissão de token quando a cidade muda).
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock DriverRepository driverRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock CityCatalog cityCatalog;
    @Mock PhoneVerificationService phoneVerificationService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock ApplicationEventPublisher events;
    @Mock com.unimove.domain.audit.AdminAuditLog auditLog;

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
        user.setPhone("5574999990000");
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
        when(jwtService.generate(user)).thenReturn(new JwtService.IssuedToken("jwt-novo", Instant.now()));
        user.setMustChangePassword(true);

        var resp = service.changePassword(auth, "senhaAtual", "senhaNova123");

        assertThat(user.getPasswordHash()).isEqualTo("hash-novo");
        // Revoga os tokens anteriores, encerra a senha temporaria e devolve token novo.
        assertThat(user.getTokensValidAfter()).isNotNull();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(resp.token()).isEqualTo("jwt-novo");
        assertThat(resp.mustChangePassword()).isFalse();
        verify(events).publishEvent(new AccountAccessRevokedEvent(USER_ID));
    }

    @Test
    @DisplayName("changePhone troca para o numero verificado no WhatsApp")
    void changePhoneHappyPath() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(phoneVerificationService.consumeVerifiedPhone("tok")).thenReturn("5574988887777");

        var resp = service.changePhone(auth, "tok");

        assertThat(user.getPhone()).isEqualTo("5574988887777");
        assertThat(user.getPhoneVerifiedAt()).isNotNull();
        assertThat(resp.phone()).isEqualTo("5574988887777");
    }

    @Test
    @DisplayName("changePhone para numero de outra conta → 409 sem alterar")
    void changePhoneInUse() {
        String before = user.getPhone();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(phoneVerificationService.consumeVerifiedPhone("tok")).thenReturn("5574911112222");
        when(userRepository.existsByPhone("5574911112222")).thenReturn(true);

        assertThatThrownBy(() -> service.changePhone(auth, "tok")).isInstanceOf(PhoneAlreadyUsedException.class);
        assertThat(user.getPhone()).isEqualTo(before);
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
    @DisplayName("changePassword em conta social diz que não há senha, em vez de 'senha atual incorreta'")
    void changePasswordOnSocialOnlyAccount() {
        user.setPasswordHash(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(auth, "qualquer", "senhaNova123"))
                .isInstanceOf(PasswordNotSetException.class);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("resetPassword dá senha a uma conta social, que passa a aceitar os dois métodos")
    void resetPasswordOnSocialOnlyAccount() {
        user.setPasswordHash(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-temporario");

        service.resetPassword(USER_ID, ADMIN_ID);

        assertThat(user.getPasswordHash()).isEqualTo("hash-temporario");
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
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getTokensValidAfter()).isNotNull();
        verify(passwordEncoder).encode(resp.temporaryPassword());
        verify(auditLog).record(ADMIN_ID, com.unimove.domain.audit.AuditAction.USER_PASSWORD_RESET,
                "USER", USER_ID, null);
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

    @Test
    @DisplayName("getProfile de passageiro não consulta drivers e vem sem bloco de veículo")
    void getProfilePassenger() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserProfileResponse resp = service.getProfile(auth);

        assertThat(resp.userId()).isEqualTo(USER_ID);
        assertThat(resp.email()).isEqualTo("p@example.com");
        assertThat(resp.vehicle()).isNull();
        verify(driverRepository, never()).findById(USER_ID);
    }

    @Test
    @DisplayName("getProfile de motorista inclui o bloco de veículo")
    void getProfileDriverIncludesVehicle() {
        user.setRole(Role.MOTORISTA);
        Driver driver = new Driver();
        driver.setVehicleType(VehicleType.MOTO);
        driver.setVehiclePlate("ABC1D23");
        driver.setApproved(true);
        driver.setOnline(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(driverRepository.findById(USER_ID)).thenReturn(Optional.of(driver));

        UserProfileResponse resp = service.getProfile(auth);

        assertThat(resp.vehicle()).isNotNull();
        assertThat(resp.vehicle().vehicleType()).isEqualTo(VehicleType.MOTO);
        assertThat(resp.vehicle().vehiclePlate()).isEqualTo("ABC1D23");
        assertThat(resp.vehicle().approved()).isTrue();
    }

    @Test
    @DisplayName("updateProfile sem mudança de cidade atualiza dados e não reemite token")
    void updateProfileSameCityNoNewToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UpdateProfileResponse resp = service.updateProfile(auth,
                new UpdateProfileRequest("  Maria S. Silva  ", "Remanso"));

        assertThat(user.getName()).isEqualTo("Maria S. Silva");
        assertThat(user.getCidade()).isEqualTo("remanso");
        assertThat(resp.token()).isNull();
        assertThat(resp.tokenExpiresAt()).isNull();
        verify(jwtService, never()).generate(user);
        // Mesma cidade: sessoes existentes seguem validas.
        assertThat(user.getTokensValidAfter()).isNull();
        verify(events, never()).publishEvent(any(AccountAccessRevokedEvent.class));
    }

    @Test
    @DisplayName("updateProfile com mudança de cidade normaliza e reemite o token")
    void updateProfileCityChangeReissuesToken() {
        Instant expires = Instant.now().plusSeconds(3600);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtService.generate(user)).thenReturn(new JwtService.IssuedToken("novo-jwt", expires));

        UpdateProfileResponse resp = service.updateProfile(auth,
                new UpdateProfileRequest("Maria Silva", "Casa Nova"));

        assertThat(user.getCidade()).isEqualTo("casa-nova");
        // Telefone verificado no cadastro permanece intocado — o perfil não o edita.
        assertThat(user.getPhone()).isEqualTo("5574999990000");
        assertThat(resp.token()).isEqualTo("novo-jwt");
        assertThat(resp.tokenExpiresAt()).isEqualTo(expires);
        assertThat(resp.profile().cidade()).isEqualTo("casa-nova");
        // Tokens com a cidade antiga deixam de valer (outro aparelho) e os SSE caem.
        assertThat(user.getTokensValidAfter()).isNotNull();
        verify(events).publishEvent(new AccountAccessRevokedEvent(USER_ID));
        // Passageiro não tem linha em drivers — nada a consultar.
        verify(driverRepository, never()).findById(USER_ID);
    }

    @Test
    @DisplayName("updateProfile rejeita cidade que normaliza para vazio")
    void updateProfileInvalidCity() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateProfile(auth,
                new UpdateProfileRequest("Maria Silva", "!!!")))
                .isInstanceOf(InvalidCityException.class);

        assertThat(user.getCidade()).isEqualTo("remanso");
    }

    @Test
    @DisplayName("updateProfile rejeita cidade fora da lista de atendimento sem alterar nada")
    void updateProfileCityNotServed() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        doThrow(new CityNotServedException()).when(cityCatalog).assertServed("xique-xique");

        assertThatThrownBy(() -> service.updateProfile(auth,
                new UpdateProfileRequest("Maria S. Silva", "Xique-Xique")))
                .isInstanceOf(CityNotServedException.class);

        // A validação vem antes de qualquer escrita: nem cidade nem nome mudam.
        assertThat(user.getCidade()).isEqualTo("remanso");
        assertThat(user.getName()).isEqualTo("Maria Silva");
        verify(jwtService, never()).generate(user);
    }

    @Test
    @DisplayName("motorista que troca de cidade é derrubado para offline")
    void updateProfileDriverGoesOfflineOnCityChange() {
        Driver driver = onlineDriver();
        user.setRole(Role.MOTORISTA);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(driverRepository.findById(USER_ID)).thenReturn(Optional.of(driver));
        when(jwtService.generate(user))
                .thenReturn(new JwtService.IssuedToken("novo-jwt", Instant.now().plusSeconds(3600)));

        UpdateProfileResponse resp = service.updateProfile(auth,
                new UpdateProfileRequest("Maria Silva", "Casa Nova"));

        assertThat(driver.isOnline()).isFalse();
        assertThat(driver.getLastSeenAt()).isNotNull();
        assertThat(resp.token()).isEqualTo("novo-jwt");
        // O app enxerga o novo estado na mesma resposta, sem refetch do perfil.
        assertThat(resp.profile().vehicle().online()).isFalse();
    }

    @Test
    @DisplayName("motorista que edita só o nome continua online e não recebe token novo")
    void updateProfileDriverStaysOnlineWhenCityUnchanged() {
        Driver driver = onlineDriver();
        user.setRole(Role.MOTORISTA);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(driverRepository.findById(USER_ID)).thenReturn(Optional.of(driver));

        UpdateProfileResponse resp = service.updateProfile(auth,
                new UpdateProfileRequest("Maria S. Silva", "Remanso"));

        assertThat(driver.isOnline()).isTrue();
        assertThat(resp.token()).isNull();
        verify(jwtService, never()).generate(user);
    }

    private static Driver onlineDriver() {
        Driver driver = new Driver();
        driver.setVehicleType(VehicleType.MOTO);
        driver.setVehiclePlate("ABC1D23");
        driver.setApproved(true);
        driver.setOnline(true);
        return driver;
    }

    @Test
    @DisplayName("esqueci minha senha: troca o hash da conta do numero, revoga sessoes e zera o bloqueio")
    void resetPasswordWithPhoneHappyPath() {
        user.setPasswordHash(null); // conta so-social ganha senha
        user.setMustChangePassword(true);
        when(phoneVerificationService.consumePasswordResetPhone("tok")).thenReturn("5574999990000");
        when(userRepository.findByPhone("5574999990000")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("senhaNova123")).thenReturn("hash-novo");

        service.resetPasswordWithPhone("tok", "senhaNova123");

        assertThat(user.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getTokensValidAfter()).isNotNull();
        verify(events).publishEvent(new AccountAccessRevokedEvent(USER_ID));
        verify(loginAttemptService).recordSuccess("p@example.com");
    }

    @Test
    @DisplayName("esqueci minha senha: token invalido nao mexe em conta nenhuma")
    void resetPasswordWithPhoneInvalidToken() {
        when(phoneVerificationService.consumePasswordResetPhone("velho"))
                .thenThrow(new PhoneVerificationRequiredException());

        assertThatThrownBy(() -> service.resetPasswordWithPhone("velho", "senhaNova123"))
                .isInstanceOf(PhoneVerificationRequiredException.class);
        verify(userRepository, never()).findByPhone(anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("esqueci minha senha: conta ADMIN e recusada")
    void resetPasswordWithPhoneRejectsAdmin() {
        user.setRole(Role.ADMIN);
        when(phoneVerificationService.consumePasswordResetPhone("tok")).thenReturn("5574999990000");
        when(userRepository.findByPhone("5574999990000")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPasswordWithPhone("tok", "senhaNova123"))
                .isInstanceOf(CannotResetAdminPasswordException.class);
        assertThat(user.getPasswordHash()).isEqualTo("hash-antigo");
        verify(events, never()).publishEvent(any());
    }
}