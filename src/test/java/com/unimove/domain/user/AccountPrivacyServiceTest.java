package com.unimove.domain.user;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RideCategory;
import com.unimove.domain.user.dto.UpdatePreferencesRequest;
import com.unimove.domain.chat.ChatPrivacyPort;
import com.unimove.domain.notification.NotificationPrivacyPort;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.RidePrivacyPort;
import com.unimove.domain.user.document.DriverDocumentService;
import com.unimove.domain.user.dto.DataExportResponse;
import com.unimove.domain.user.dto.PrivacyPreferencesResponse;
import com.unimove.domain.user.social.SocialIdentityEntity;
import com.unimove.domain.user.social.SocialIdentityRepository;
import com.unimove.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.unimove.shared.event.AccountAccessRevokedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPrivacyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String TERMS = "2026-10";

    @Mock UserRepository userRepository;
    @Mock DriverRepository driverRepository;
    @Mock SavedPlaceRepository savedPlaceRepository;
    @Mock SocialIdentityRepository socialIdentityRepository;
    @Mock DriverDocumentService documentService;
    @Mock UserProfileService profileService;
    @Mock RidePrivacyPort ridePrivacy;
    @Mock ChatPrivacyPort chatPrivacy;
    @Mock NotificationPrivacyPort notificationPrivacy;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PaymentService paymentService;
    @Mock ApplicationEventPublisher events;

    private AccountPrivacyService service;
    private User user;
    private AuthenticatedUser auth;

    @BeforeEach
    void setUp() {
        service = new AccountPrivacyService(userRepository, driverRepository, savedPlaceRepository,
                socialIdentityRepository, documentService, profileService, ridePrivacy, chatPrivacy, notificationPrivacy,
                passwordEncoder, paymentService, new PrivacyProperties(TERMS, "https://x/privacidade"), events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("maria@x.com");
        user.setName("Maria Silva");
        user.setPhone("5574999990000");
        user.setPhoneVerifiedAt(NOW);
        user.setPasswordHash("hash");
        user.setRole(Role.PASSAGEIRO);
        user.setCidade("remanso-ba");
        auth = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), user.getCidade());
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        lenient().when(passwordEncoder.matches("senha-certa", "hash")).thenReturn(true);
    }

    // ------------------------------------------------------------------------
    // exclusao
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("exclusao anonimiza o que identifica e apaga locais, vinculo social, mensagens e comentarios")
    void deleteAnonymizes() {
        List<SocialIdentityEntity> links = List.of(new SocialIdentityEntity());
        when(socialIdentityRepository.findByUserId(user.getId())).thenReturn(links);

        service.deleteAccount(auth, "senha-certa");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isEqualTo(NOW);
        assertThat(user.getEmail()).isEqualTo("deleted-" + user.getId() + "@removed.invalid");
        assertThat(user.getName()).isEqualTo(AccountPrivacyService.REMOVED_NAME);
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        verify(savedPlaceRepository).deleteAllByUser(user.getId());
        verify(socialIdentityRepository).deleteAll(links);
        verify(chatPrivacy).deleteSentBy(user.getId());
        verify(ridePrivacy).eraseRatingComments(user.getId());
        verify(notificationPrivacy).deleteInboxOf(user.getId());
        verify(documentService, never()).purgeFiles(any());
        verify(events).publishEvent(new AccountAccessRevokedEvent(user.getId()));
    }

    @Test
    @DisplayName("exclusao de motorista apaga CPF/CNH/veiculo e por ultimo os arquivos do storage")
    void deleteDriverPurgesDocuments() {
        user.setRole(Role.MOTORISTA);
        Driver driver = new Driver();
        driver.setApproved(true);
        driver.setOnline(true);
        driver.setCpf("52998224725");
        driver.setCnhNumber("12345678901");
        driver.setCnhExpiresOn(LocalDate.of(2030, 1, 1));
        driver.setVehiclePlate("ABC1234");
        when(driverRepository.findById(user.getId())).thenReturn(Optional.of(driver));

        service.deleteAccount(auth, "senha-certa");

        assertThat(driver.getCpf()).isNull();
        assertThat(driver.getCnhNumber()).isNull();
        assertThat(driver.getVehiclePlate()).isEqualTo(AccountPrivacyService.REMOVED_PLATE);
        assertThat(driver.isApproved()).isFalse();
        assertThat(driver.isOnline()).isFalse();
        InOrder order = inOrder(chatPrivacy, documentService);
        order.verify(chatPrivacy).deleteSentBy(user.getId());
        order.verify(documentService).purgeFiles(user.getId());
    }

    @Test
    @DisplayName("senha errada → 400 e nada e apagado")
    void deleteRequiresPassword() {
        assertThatThrownBy(() -> service.deleteAccount(auth, "errada"))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        assertThatThrownBy(() -> service.deleteAccount(auth, null))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(savedPlaceRepository, never()).deleteAllByUser(any());
    }

    @Test
    @DisplayName("conta so-social (sem senha) exclui sem senha")
    void deleteSocialAccountWithoutPassword() {
        user.setPasswordHash(null);

        service.deleteAccount(auth, null);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    @DisplayName("corrida em andamento → 409 e nada e apagado")
    void deleteBlockedByActiveRide() {
        when(ridePrivacy.hasActiveRide(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteAccount(auth, "senha-certa"))
                .isInstanceOf(AccountDeletionBlockedException.class)
                .hasMessageContaining("corrida");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(chatPrivacy, never()).deleteSentBy(any());
    }

    @Test
    @DisplayName("admin nao se exclui pelo app → 403")
    void adminCannotDelete() {
        user.setRole(Role.ADMIN);

        assertThatThrownBy(() -> service.deleteAccount(auth, "senha-certa"))
                .isInstanceOf(AccountDeletionBlockedException.class);
    }

    // ------------------------------------------------------------------------
    // preferencias e termos
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("opt-in promocional grava quando; opt-out zera; transacional sempre ligado")
    void marketingOptInAndOut() {
        PrivacyPreferencesResponse in = service.updatePreferences(auth, new UpdatePreferencesRequest(true, null, null));
        assertThat(in.marketingOptIn()).isTrue();
        assertThat(in.marketingOptInAt()).isEqualTo(NOW);
        assertThat(in.transactionalNotifications()).isTrue();

        PrivacyPreferencesResponse out = service.updatePreferences(auth, new UpdatePreferencesRequest(false, null, null));
        assertThat(out.marketingOptIn()).isFalse();
        assertThat(out.marketingOptInAt()).isNull();
    }

    @Test
    @DisplayName("passageiro guarda pagamento e categoria preferidos; PUT sem eles limpa")
    void passengerRidePreferences() {
        when(paymentService.isPixEnabled()).thenReturn(true);
        PrivacyPreferencesResponse set = service.updatePreferences(auth,
                new UpdatePreferencesRequest(false, PaymentMethod.PIX, RideCategory.MOTO));
        assertThat(set.preferredPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(set.preferredCategory()).isEqualTo(RideCategory.MOTO);
        assertThat(service.getPreferences(auth).preferredCategory()).isEqualTo(RideCategory.MOTO);

        PrivacyPreferencesResponse cleared = service.updatePreferences(auth,
                new UpdatePreferencesRequest(false, null, null));
        assertThat(cleared.preferredPaymentMethod()).isNull();
        assertThat(cleared.preferredCategory()).isNull();
    }

    @Test
    @DisplayName("Pix desligado (piloto so dinheiro): preferir Pix → 400 e nada muda; dinheiro passa")
    void pixPreferenceRejectedWhenPixDisabled() {
        user.setPreferredPaymentMethod(PaymentMethod.DINHEIRO);

        assertThatThrownBy(() -> service.updatePreferences(auth,
                new UpdatePreferencesRequest(true, PaymentMethod.PIX, RideCategory.MOTO)))
                .isInstanceOf(InvalidPreferenceException.class)
                .hasMessageContaining("Pix");
        assertThat(user.getPreferredPaymentMethod()).isEqualTo(PaymentMethod.DINHEIRO);
        assertThat(user.getPreferredCategory()).isNull();
        assertThat(user.isMarketingOptIn()).isFalse();

        assertThat(service.updatePreferences(auth,
                new UpdatePreferencesRequest(false, PaymentMethod.DINHEIRO, null)).preferredPaymentMethod())
                .isEqualTo(PaymentMethod.DINHEIRO);
    }

    @Test
    @DisplayName("motorista com pagamento/categoria preferidos → 400; so opt-in passa")
    void driverCannotSetRidePreferences() {
        user.setRole(Role.MOTORISTA);

        assertThatThrownBy(() -> service.updatePreferences(auth,
                new UpdatePreferencesRequest(true, null, RideCategory.CARRO)))
                .isInstanceOf(InvalidPreferenceException.class);
        assertThat(user.isMarketingOptIn()).isFalse();
        assertThat(service.updatePreferences(auth, new UpdatePreferencesRequest(true, null, null))
                .marketingOptIn()).isTrue();
    }

    @Test
    @DisplayName("aceite da versao vigente atualiza o consentimento")
    void acceptCurrentTerms() {
        user.setTermsVersion("2025-01");
        assertThat(service.getPreferences(auth).termsUpToDate()).isFalse();

        PrivacyPreferencesResponse resp = service.acceptTerms(auth, TERMS);

        assertThat(resp.termsUpToDate()).isTrue();
        assertThat(user.getTermsAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("aceite de versao que nao e a vigente → 400")
    void acceptWrongTerms() {
        assertThatThrownBy(() -> service.acceptTerms(auth, "1999-01"))
                .isInstanceOf(TermsNotAcceptedException.class);
    }

    // ------------------------------------------------------------------------
    // exportacao
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("exportacao junta perfil, locais, corridas e mensagens; passageiro sem bloco de documentos")
    void exportPassenger() {
        when(savedPlaceRepository.findAllByUser(user.getId())).thenReturn(List.of());
        when(ridePrivacy.exportRides(user.getId())).thenReturn(List.of());
        when(chatPrivacy.exportSentBy(user.getId())).thenReturn(List.of());

        DataExportResponse resp = service.export(auth);

        assertThat(resp.generatedAt()).isEqualTo(NOW);
        assertThat(resp.driverDocumentation()).isNull();
        verify(profileService).getProfile(auth);
        verify(documentService, never()).getDocumentation(any());
    }

    @Test
    @DisplayName("exportacao de motorista inclui a documentacao declarada")
    void exportDriver() {
        user.setRole(Role.MOTORISTA);

        service.export(auth);

        verify(documentService).getDocumentation(user.getId());
    }
}
