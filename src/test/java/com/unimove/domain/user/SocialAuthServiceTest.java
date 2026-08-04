package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.SocialAuthResponse;
import com.unimove.domain.user.dto.SocialLoginRequest;
import com.unimove.domain.user.dto.SocialRegisterRequest;
import com.unimove.domain.user.social.SocialEmailNotVerifiedException;
import com.unimove.domain.user.social.SocialIdentity;
import com.unimove.domain.user.social.SocialIdentityEntity;
import com.unimove.domain.user.social.SocialIdentityRepository;
import com.unimove.domain.user.social.SocialIdentityVerifier;
import com.unimove.domain.user.social.SocialLoginUnavailableException;
import com.unimove.domain.user.social.SocialProvider;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final String ID_TOKEN = "eyJ-token-do-app";
    private static final String PHONE_TOKEN = "token-verificado";
    /** Telefone como a Meta entrega: E.164 sem '+'. */
    private static final String VERIFIED_PHONE = "5574999990000";
    private static final String EMAIL = "maria@example.com";
    private static final String SUBJECT = "google-sub-123";

    @Mock SocialIdentityVerifier verifier;
    @Mock UserRepository userRepository;
    @Mock DriverRepository driverRepository;
    @Mock SocialIdentityRepository socialIdentityRepository;
    @Mock PhoneVerificationService phoneVerificationService;
    @Mock JwtService jwtService;

    private SocialAuthService service;

    @BeforeEach
    void setUp() {
        // O service indexa os verifiers por provedor no construtor.
        when(verifier.provider()).thenReturn(SocialProvider.GOOGLE);
        service = newService(List.of(verifier));
    }

    private SocialAuthService newService(List<SocialIdentityVerifier> verifiers) {
        return new SocialAuthService(verifiers, userRepository, driverRepository, socialIdentityRepository,
                phoneVerificationService, jwtService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Encurta o arranjo: o provedor devolve a identidade pedida. */
    private void givenVerifiedIdentity(boolean emailVerified) {
        when(verifier.verify(ID_TOKEN)).thenReturn(
                new SocialIdentity(SocialProvider.GOOGLE, SUBJECT, EMAIL, emailVerified, "Maria Silva"));
    }

    private void givenTokenIsIssued() {
        when(jwtService.generate(any()))
                .thenReturn(new JwtService.IssuedToken("jwt", NOW.plusSeconds(3600)));
    }

    private static User existingUser(UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setRole(Role.PASSAGEIRO);
        user.setCidade("remanso");
        user.setStatus(status);
        return user;
    }

    private static SocialIdentityEntity link(UUID userId) {
        SocialIdentityEntity link = new SocialIdentityEntity();
        link.setUserId(userId);
        link.setProvider(SocialProvider.GOOGLE);
        link.setSubject(SUBJECT);
        link.setEmail(EMAIL);
        return link;
    }

    private static SocialRegisterRequest registerRequest(Role role, VehicleType type, String plate) {
        return new SocialRegisterRequest(SocialProvider.GOOGLE, ID_TOKEN, PHONE_TOKEN,
                role, "Remanso", type, plate);
    }

    // ---------- login ----------

    @Test
    @DisplayName("identidade já vinculada autentica direto")
    void loginWithLinkedIdentityAuthenticates() {
        User user = existingUser(UserStatus.ACTIVE);
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(link(user.getId())));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        givenTokenIsIssued();

        SocialAuthResponse resp = service.login(new SocialLoginRequest(SocialProvider.GOOGLE, ID_TOKEN));

        assertThat(resp.status()).isEqualTo(SocialAuthResponse.Status.AUTHENTICATED);
        assertThat(resp.auth().token()).isEqualTo("jwt");
        assertThat(resp.profile()).isNull();
        // Ja vinculada: nao cria vinculo de novo.
        verify(socialIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("conta pré-existente com o mesmo e-mail é vinculada e autentica")
    void loginLinksExistingAccountByVerifiedEmail() {
        User user = existingUser(UserStatus.ACTIVE);
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        givenTokenIsIssued();

        SocialAuthResponse resp = service.login(new SocialLoginRequest(SocialProvider.GOOGLE, ID_TOKEN));

        assertThat(resp.status()).isEqualTo(SocialAuthResponse.Status.AUTHENTICATED);
        ArgumentCaptor<SocialIdentityEntity> captor = ArgumentCaptor.forClass(SocialIdentityEntity.class);
        verify(socialIdentityRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(captor.getValue().getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("usuário novo devolve REGISTRATION_REQUIRED com dados para pré-preencher")
    void loginWithUnknownIdentityAsksForRegistration() {
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        SocialAuthResponse resp = service.login(new SocialLoginRequest(SocialProvider.GOOGLE, ID_TOKEN));

        assertThat(resp.status()).isEqualTo(SocialAuthResponse.Status.REGISTRATION_REQUIRED);
        assertThat(resp.auth()).isNull();
        assertThat(resp.profile().email()).isEqualTo(EMAIL);
        assertThat(resp.profile().name()).isEqualTo("Maria Silva");
        verify(socialIdentityRepository, never()).save(any());
        verify(jwtService, never()).generate(any());
    }

    @Test
    @DisplayName("e-mail não verificado no provedor bloqueia antes de qualquer vínculo")
    void loginWithUnverifiedEmailIsRejected() {
        givenVerifiedIdentity(false);

        assertThatThrownBy(() -> service.login(new SocialLoginRequest(SocialProvider.GOOGLE, ID_TOKEN)))
                .isInstanceOf(SocialEmailNotVerifiedException.class);

        // Sem esta guarda, quem controlasse um e-mail alheio no provedor
        // assumiria a conta existente.
        verify(socialIdentityRepository, never()).findByProviderAndSubject(any(), anyString());
        verify(socialIdentityRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuário suspenso não entra pelo login social")
    void loginWithSuspendedUserIsRejected() {
        User user = existingUser(UserStatus.SUSPENDED);
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(link(user.getId())));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new SocialLoginRequest(SocialProvider.GOOGLE, ID_TOKEN)))
                .isInstanceOf(UserSuspendedException.class);

        verify(jwtService, never()).generate(any());
    }

    @Test
    @DisplayName("ambiente sem provedor configurado responde indisponível")
    void loginWithoutConfiguredProviderIsUnavailable() {
        SocialAuthService disabled = newService(List.of());

        assertThatThrownBy(() -> disabled.login(new SocialLoginRequest(SocialProvider.GOOGLE, ID_TOKEN)))
                .isInstanceOf(SocialLoginUnavailableException.class);
    }

    // ---------- register ----------

    @Test
    @DisplayName("passageiro nasce sem senha, com o telefone verificado e já vinculado")
    void registerCreatesPasswordlessUserAndLink() {
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(phoneVerificationService.consumeVerifiedPhone(PHONE_TOKEN)).thenReturn(VERIFIED_PHONE);
        when(userRepository.save(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID()); // o @PrePersist faz isso em producao
            return saved;
        });
        givenTokenIsIssued();

        AuthResponse resp = service.register(registerRequest(Role.PASSAGEIRO, null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getName()).isEqualTo("Maria Silva");
        assertThat(saved.getCidade()).isEqualTo("remanso");
        assertThat(saved.getPhone()).isEqualTo(VERIFIED_PHONE);
        assertThat(saved.getPhoneVerifiedAt()).isEqualTo(NOW);

        verify(socialIdentityRepository).save(any());
        verify(driverRepository, never()).save(any());
        assertThat(resp.token()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("motorista social também nasce pendente de aprovação")
    void registerMotoristaCreatesPendingDriver() {
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(phoneVerificationService.consumeVerifiedPhone(PHONE_TOKEN)).thenReturn(VERIFIED_PHONE);
        givenTokenIsIssued();

        service.register(registerRequest(Role.MOTORISTA, VehicleType.MOTO, " abc1d23 "));

        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository).save(captor.capture());
        assertThat(captor.getValue().isApproved()).isFalse();
        assertThat(captor.getValue().isOnline()).isFalse();
        assertThat(captor.getValue().getVehiclePlate()).isEqualTo("ABC1D23");
    }

    @Test
    @DisplayName("cadastro social como ADMIN é bloqueado antes de validar o token")
    void registerAsAdminIsBlocked() {
        assertThatThrownBy(() -> service.register(registerRequest(Role.ADMIN, null, null)))
                .isInstanceOf(RoleNotSelfAssignableException.class);

        verify(verifier, never()).verify(anyString());
        verify(userRepository, never()).save(any());
        verify(phoneVerificationService, never()).consumeVerifiedPhone(anyString());
    }

    @Test
    @DisplayName("conta já existente manda o app voltar ao /auth/social, sem gastar a verificação")
    void registerWithExistingAccountIsRejected() {
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest(Role.PASSAGEIRO, null, null)))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(phoneVerificationService, never()).consumeVerifiedPhone(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("verificação de telefone inválida impede o cadastro social")
    void registerWithInvalidPhoneTokenIsRejected() {
        givenVerifiedIdentity(true);
        when(socialIdentityRepository.findByProviderAndSubject(SocialProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(phoneVerificationService.consumeVerifiedPhone(PHONE_TOKEN))
                .thenThrow(new PhoneVerificationRequiredException());

        assertThatThrownBy(() -> service.register(registerRequest(Role.PASSAGEIRO, null, null)))
                .isInstanceOf(PhoneVerificationRequiredException.class);

        verify(userRepository, never()).save(any());
        verify(socialIdentityRepository, never()).save(any());
        verify(jwtService, never()).generate(any());
    }
}
