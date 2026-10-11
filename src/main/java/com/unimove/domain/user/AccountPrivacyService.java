package com.unimove.domain.user;

import com.unimove.domain.chat.ChatPrivacyPort;
import com.unimove.domain.notification.NotificationPrivacyPort;
import com.unimove.domain.payment.PaymentService;
import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RidePrivacyPort;
import com.unimove.domain.user.document.DriverDocumentService;
import com.unimove.domain.user.dto.DataExportResponse;
import com.unimove.domain.user.dto.PrivacyPreferencesResponse;
import com.unimove.domain.user.dto.UpdatePreferencesRequest;
import com.unimove.domain.user.social.SocialIdentityRepository;
import com.unimove.shared.event.AccountAccessRevokedEvent;
import com.unimove.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Direitos do titular (LGPD art. 18): acesso/portabilidade (exportacao),
 * eliminacao (exclusao com anonimizacao), consentimento e preferencias.
 *
 * <p>A exclusao anonimiza em vez de apagar a linha: corridas, avaliacoes e
 * pagamentos de outras pessoas apontam para ela e ficam retidos por obrigacao
 * legal/defesa em disputa. Tudo o que identifica o titular — nome, e-mail,
 * telefone, senha, CPF/CNH, arquivos de documento, locais salvos, vinculo
 * social, mensagens e comentarios — sai na hora.
 */
@Service
public class AccountPrivacyService {

    private static final Logger log = LoggerFactory.getLogger(AccountPrivacyService.class);

    static final String REMOVED_NAME = "Usuário removido";
    static final String REMOVED_PLATE = "REMOVIDO";

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final SocialIdentityRepository socialIdentityRepository;
    private final DriverDocumentService documentService;
    private final UserProfileService profileService;
    private final RidePrivacyPort ridePrivacy;
    private final ChatPrivacyPort chatPrivacy;
    private final NotificationPrivacyPort notificationPrivacy;
    private final PasswordEncoder passwordEncoder;
    private final PaymentService paymentService;
    private final PrivacyProperties privacy;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountPrivacyService(UserRepository userRepository,
                                 DriverRepository driverRepository,
                                 SavedPlaceRepository savedPlaceRepository,
                                 SocialIdentityRepository socialIdentityRepository,
                                 DriverDocumentService documentService,
                                 UserProfileService profileService,
                                 RidePrivacyPort ridePrivacy,
                                 ChatPrivacyPort chatPrivacy,
                                 NotificationPrivacyPort notificationPrivacy,
                                 PasswordEncoder passwordEncoder,
                                 PaymentService paymentService,
                                 PrivacyProperties privacy,
                                 ApplicationEventPublisher events,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.savedPlaceRepository = savedPlaceRepository;
        this.socialIdentityRepository = socialIdentityRepository;
        this.documentService = documentService;
        this.profileService = profileService;
        this.ridePrivacy = ridePrivacy;
        this.chatPrivacy = chatPrivacy;
        this.notificationPrivacy = notificationPrivacy;
        this.passwordEncoder = passwordEncoder;
        this.paymentService = paymentService;
        this.privacy = privacy;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------
    // Preferencias e consentimento
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PrivacyPreferencesResponse getPreferences(AuthenticatedUser auth) {
        return toPreferences(load(auth.userId()));
    }

    @Transactional
    public PrivacyPreferencesResponse updatePreferences(AuthenticatedUser auth, UpdatePreferencesRequest req) {
        User user = load(auth.userId());
        boolean hasRidePreference = req.preferredPaymentMethod() != null || req.preferredCategory() != null;
        if (hasRidePreference && user.getRole() != Role.PASSAGEIRO) {
            throw new InvalidPreferenceException();
        }
        // Pix desligado (piloto so dinheiro): o app nao pode pre-selecionar Pix e so
        // descobrir o 409 no confirm-payment.
        if (req.preferredPaymentMethod() == PaymentMethod.PIX && !paymentService.isPixEnabled()) {
            throw InvalidPreferenceException.pixUnavailable();
        }
        // PUT substitui: ausente = sem preferencia.
        user.setPreferredPaymentMethod(req.preferredPaymentMethod());
        user.setPreferredCategory(req.preferredCategory());
        boolean marketingOptIn = req.marketingOptIn();
        if (user.isMarketingOptIn() != marketingOptIn) {
            user.setMarketingOptIn(marketingOptIn);
            // Guarda quando o opt-in foi dado; revogado, fica nulo.
            user.setMarketingOptInAt(marketingOptIn ? clock.instant() : null);
            log.info("Usuario {} {} comunicacao promocional", user.getId(), marketingOptIn ? "aceitou" : "recusou");
        }
        return toPreferences(user);
    }

    @Transactional
    public PrivacyPreferencesResponse acceptTerms(AuthenticatedUser auth, String version) {
        if (!privacy.termsVersion().equals(version)) {
            throw new TermsNotAcceptedException(privacy.termsVersion());
        }
        User user = load(auth.userId());
        user.setTermsVersion(version);
        user.setTermsAcceptedAt(clock.instant());
        log.info("Usuario {} aceitou os termos versao {}", user.getId(), version);
        return toPreferences(user);
    }

    // ------------------------------------------------------------------------
    // Exportacao
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DataExportResponse export(AuthenticatedUser auth) {
        User user = load(auth.userId());
        log.info("Exportacao de dados pessoais solicitada pelo usuario {}", user.getId());
        return new DataExportResponse(
                clock.instant(),
                profileService.getProfile(auth),
                toPreferences(user),
                savedPlaceRepository.findAllByUser(user.getId()),
                ridePrivacy.exportRides(user.getId()),
                chatPrivacy.exportSentBy(user.getId()),
                user.getRole() == Role.MOTORISTA ? documentService.getDocumentation(user.getId()) : null);
    }

    // ------------------------------------------------------------------------
    // Exclusao
    // ------------------------------------------------------------------------

    @Transactional
    public void deleteAccount(AuthenticatedUser auth, String password) {
        User user = load(auth.userId());
        if (user.getRole() == Role.ADMIN) {
            throw AccountDeletionBlockedException.admin();
        }
        if (user.getPasswordHash() != null
                && (password == null || !passwordEncoder.matches(password, user.getPasswordHash()))) {
            throw new InvalidCurrentPasswordException();
        }
        if (ridePrivacy.hasActiveRide(user.getId())) {
            throw AccountDeletionBlockedException.activeRide();
        }

        UUID id = user.getId();
        savedPlaceRepository.deleteAllByUser(id);
        socialIdentityRepository.deleteAll(socialIdentityRepository.findByUserId(id));
        int messages = chatPrivacy.deleteSentBy(id);
        int comments = ridePrivacy.eraseRatingComments(id);
        notificationPrivacy.deleteInboxOf(id);

        Instant now = clock.instant();
        user.setEmail("deleted-" + id + "@removed.invalid");
        user.setName(REMOVED_NAME);
        user.setPhone(null);
        user.setPhoneVerifiedAt(null);
        user.setPasswordHash(null);
        user.setMarketingOptIn(false);
        user.setMarketingOptInAt(null);
        user.setPreferredPaymentMethod(null);
        user.setPreferredCategory(null);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(now);
        events.publishEvent(new AccountAccessRevokedEvent(id));

        int files = 0;
        if (user.getRole() == Role.MOTORISTA) {
            driverRepository.findById(id).ifPresent(AccountPrivacyService::anonymizeDriver);
            // Por ultimo e dentro da transacao: se o storage falhar, nada foi
            // anonimizado e o usuario pode tentar de novo — nunca dizemos
            // "excluido" com o documento ainda la.
            files = documentService.purgeFiles(id);
        }

        log.info("Conta {} excluida a pedido do titular (mensagens={}, comentarios={}, arquivos={})",
                id, messages, comments, files);
    }

    private static void anonymizeDriver(Driver d) {
        d.setApproved(false);
        d.setOnline(false);
        d.setCpf(null);
        d.setCpfHash(null);
        d.setCnhNumber(null);
        d.setCnhNumberHash(null);
        d.setCnhCategory(null);
        d.setCnhExpiresOn(null);
        d.setVehicleBrand(null);
        d.setVehicleModel(null);
        d.setVehicleYear(null);
        d.setVehicleColor(null);
        d.setVehiclePlate(REMOVED_PLATE);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private PrivacyPreferencesResponse toPreferences(User user) {
        return new PrivacyPreferencesResponse(
                user.isMarketingOptIn(),
                user.getMarketingOptInAt(),
                true,
                user.getTermsVersion(),
                user.getTermsAcceptedAt(),
                privacy.termsVersion(),
                privacy.termsVersion().equals(user.getTermsVersion()),
                user.getPreferredPaymentMethod(),
                user.getPreferredCategory());
    }
}
