package com.unimove.domain.user;

import com.unimove.domain.city.CityCatalog;
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
import com.unimove.domain.verification.PhoneVerificationService;
import com.unimove.shared.security.JwtService;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Entrada por provedor social. O provedor prova a identidade (substituindo a
 * senha); a posse do telefone continua vindo do desafio do WhatsApp, como no
 * cadastro comum.
 *
 * <p>Nao usa {@link LoginAttemptService}: nao existe forca bruta contra um
 * token assinado pelo provedor, e travar por e-mail so criaria um vetor de
 * negacao de servico contra a conta alheia.
 */
@Service
public class SocialAuthService {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);

    private final Map<SocialProvider, SocialIdentityVerifier> verifiers = new EnumMap<>(SocialProvider.class);
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final SocialIdentityRepository socialIdentityRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final JwtService jwtService;
    private final CityCatalog cityCatalog;
    private final Clock clock;

    public SocialAuthService(List<SocialIdentityVerifier> verifiers,
                             UserRepository userRepository,
                             DriverRepository driverRepository,
                             SocialIdentityRepository socialIdentityRepository,
                             PhoneVerificationService phoneVerificationService,
                             JwtService jwtService,
                             CityCatalog cityCatalog,
                             Clock clock) {
        verifiers.forEach(v -> this.verifiers.put(v.provider(), v));
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.socialIdentityRepository = socialIdentityRepository;
        this.phoneVerificationService = phoneVerificationService;
        this.jwtService = jwtService;
        this.cityCatalog = cityCatalog;
        this.clock = clock;
    }

    /**
     * Entra com a conta do provedor. Se o usuario ainda nao existe, devolve
     * {@code REGISTRATION_REQUIRED} em vez de erro — o cadastro e a
     * continuacao natural do fluxo.
     */
    @Transactional
    public SocialAuthResponse login(SocialLoginRequest req) {
        SocialIdentity identity = verifyIdentity(req.provider(), req.idToken());

        User user = socialIdentityRepository
                .findByProviderAndSubject(identity.provider(), identity.subject())
                .map(link -> userRepository.findById(link.getUserId()).orElseThrow(UserNotFoundException::new))
                .orElseGet(() -> linkExistingAccount(identity));

        if (user == null) {
            return SocialAuthResponse.registrationRequired(identity.email(), identity.name());
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserSuspendedException();
        }
        return SocialAuthResponse.authenticated(AuthResponse.of(user, jwtService.generate(user)));
    }

    /**
     * Completa o primeiro acesso. Espelha {@link AuthService#register} — a
     * unica diferenca e a origem da identidade: token do provedor no lugar de
     * e-mail + senha.
     */
    @Transactional
    public AuthResponse register(SocialRegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            log.warn("Tentativa de auto-cadastro social como ADMIN bloqueada");
            throw new RoleNotSelfAssignableException();
        }

        SocialIdentity identity = verifyIdentity(req.provider(), req.idToken());

        boolean alreadyLinked = socialIdentityRepository
                .findByProviderAndSubject(identity.provider(), identity.subject())
                .isPresent();
        if (alreadyLinked || userRepository.existsByEmail(identity.email())) {
            // A conta ja existe: o app deve refazer POST /auth/social, que
            // autentica (e vincula, se for o caso).
            throw new EmailAlreadyUsedException();
        }

        String cidade = CityNormalizer.normalize(req.cidade());
        if (cidade.isEmpty()) {
            throw new InvalidCityException();
        }
        cityCatalog.assertServed(cidade);

        // Mesma transacao do cadastro: se algo falhar adiante, o rollback
        // devolve o token de verificacao e o usuario nao refaz o WhatsApp.
        String verifiedPhone = phoneVerificationService.consumeVerifiedPhone(req.verificationToken());

        User user = new User();
        user.setEmail(identity.email());
        user.setPasswordHash(null); // conta so-social; ganha senha depois via reset do admin
        user.setName(displayName(identity));
        user.setPhone(verifiedPhone);
        user.setPhoneVerifiedAt(clock.instant());
        user.setRole(req.role());
        user.setCidade(cidade);
        userRepository.save(user);

        if (req.role() == Role.MOTORISTA) {
            Driver driver = new Driver();
            driver.setUser(user);
            driver.setApproved(false);
            driver.setOnline(false);
            driver.setVehicleType(req.vehicleType());
            driver.setVehiclePlate(req.vehiclePlate().trim().toUpperCase());
            driverRepository.save(driver);
        }

        link(user, identity);

        log.info("Novo cadastro social: userId={}, provider={}, role={}, cidade={}",
                user.getId(), identity.provider(), user.getRole(), user.getCidade());
        return AuthResponse.of(user, jwtService.generate(user));
    }

    private SocialIdentity verifyIdentity(SocialProvider provider, String idToken) {
        SocialIdentityVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw new SocialLoginUnavailableException();
        }
        SocialIdentity identity = verifier.verify(idToken);
        if (!identity.emailVerified()) {
            log.warn("Login social recusado: e-mail nao verificado no provedor {}", provider);
            throw new SocialEmailNotVerifiedException();
        }
        return identity;
    }

    /**
     * Vincula a identidade a uma conta pre-existente com o mesmo e-mail. So e
     * seguro porque {@code verifyIdentity} ja exigiu e-mail verificado no
     * provedor. Devolve {@code null} quando nao ha conta — sinal de cadastro
     * pendente.
     */
    private User linkExistingAccount(SocialIdentity identity) {
        User user = userRepository.findByEmail(identity.email()).orElse(null);
        if (user == null) {
            return null;
        }
        link(user, identity);
        log.info("Conta vinculada ao provedor social: userId={}, provider={}", user.getId(), identity.provider());
        return user;
    }

    private void link(User user, SocialIdentity identity) {
        SocialIdentityEntity link = new SocialIdentityEntity();
        link.setUserId(user.getId());
        link.setProvider(identity.provider());
        link.setSubject(identity.subject());
        link.setEmail(identity.email());
        socialIdentityRepository.save(link);
    }

    /** O provedor pode nao mandar {@code name}; o e-mail vira o nome inicial. */
    private static String displayName(SocialIdentity identity) {
        String name = identity.name() == null ? "" : identity.name().trim();
        if (name.isEmpty()) {
            name = identity.email();
        }
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
