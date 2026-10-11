package com.unimove.domain.user;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.city.CityCatalog;
import com.unimove.domain.user.dto.AdminResetPasswordResponse;
import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.UpdateProfileRequest;
import com.unimove.domain.user.dto.UpdateProfileResponse;
import com.unimove.domain.user.dto.UserProfileResponse;
import com.unimove.domain.verification.PhoneVerificationService;
import com.unimove.shared.event.AccountAccessRevokedEvent;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.security.JwtService;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    // Sem caracteres ambíguos (0/O, 1/l/I) — a senha temporária é ditada por telefone/WhatsApp.
    private static final String TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CityCatalog cityCatalog;
    private final AdminAuditLog auditLog;
    private final PhoneVerificationService phoneVerificationService;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public UserProfileService(UserRepository userRepository,
                              DriverRepository driverRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              CityCatalog cityCatalog,
                              AdminAuditLog auditLog,
                              PhoneVerificationService phoneVerificationService,
                              LoginAttemptService loginAttemptService,
                              ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cityCatalog = cityCatalog;
        this.auditLog = auditLog;
        this.phoneVerificationService = phoneVerificationService;
        this.loginAttemptService = loginAttemptService;
        this.events = events;
    }

    /**
     * Troca o telefone pelo numero provado num novo desafio do WhatsApp. O
     * desafio ja recusa numero de outra conta (PHONE_IN_USE); a checagem aqui
     * fecha a corrida entre o desafio e esta chamada — e o indice unico
     * ux_users_phone fecha o resto.
     */
    @Transactional
    public UserProfileResponse changePhone(AuthenticatedUser auth, String verificationToken) {
        User user = userRepository.findById(auth.userId())
                .orElseThrow(UserNotFoundException::new);
        String phone = phoneVerificationService.consumeVerifiedPhone(verificationToken);
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new PhoneAlreadyUsedException();
        }
        user.setPhone(phone);
        user.setPhoneVerifiedAt(Instant.now());
        log.info("Usuario {} trocou o telefone (verificado via WhatsApp)", user.getId());
        Driver driver = user.getRole() == Role.MOTORISTA
                ? driverRepository.findById(user.getId()).orElse(null)
                : null;
        return UserProfileResponse.from(user, driver);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(AuthenticatedUser auth) {
        User user = userRepository.findById(auth.userId())
                .orElseThrow(UserNotFoundException::new);
        Driver driver = user.getRole() == Role.MOTORISTA
                ? driverRepository.findById(user.getId()).orElse(null)
                : null;
        return UserProfileResponse.from(user, driver);
    }

    /**
     * Atualiza nome e cidade. E-mail (login) e role são imutáveis; o telefone
     * só muda por novo desafio do WhatsApp ({@link #changePhone}) — editá-lo
     * aqui anularia aquela verificação.
     * Como o JWT carrega a claim {@code cidade}, mudança de cidade reemite o
     * token — sem isso, mural e criação de corrida continuariam usando a
     * cidade antiga até o re-login.
     *
     * <p>Tokens anteriores sao revogados: com a claim {@code cidade} antiga, uma
     * sessao em outro aparelho continuaria operando na cidade abandonada. O
     * token novo vale (iat comparado truncado ao segundo) e os streams SSE
     * abertos com o velho sao encerrados — o cliente os reabre com o novo.
     *
     * <p>Motorista que muda de cidade é derrubado para offline: continuar
     * "online" num mural que ele não vê mais o manteria no denominador do surge
     * da cidade que abandonou. Ele reentra pelo {@code POST /drivers/me/online}
     * quando estiver de fato disponível na cidade nova.
     */
    @Transactional
    public UpdateProfileResponse updateProfile(AuthenticatedUser auth, UpdateProfileRequest req) {
        User user = userRepository.findById(auth.userId())
                .orElseThrow(UserNotFoundException::new);

        String cidade = CityNormalizer.normalize(req.cidade());
        if (cidade == null || cidade.isEmpty()) {
            throw new InvalidCityException();
        }
        cityCatalog.assertServed(cidade);
        boolean cidadeChanged = !cidade.equals(user.getCidade());

        user.setName(req.name().trim());
        user.setCidade(cidade);

        String token = null;
        Instant tokenExpiresAt = null;
        if (cidadeChanged) {
            user.setTokensValidAfter(Instant.now());
            events.publishEvent(new AccountAccessRevokedEvent(user.getId()));
            JwtService.IssuedToken issued = jwtService.generate(user);
            token = issued.token();
            tokenExpiresAt = issued.expiresAt();
        }

        Driver driver = user.getRole() == Role.MOTORISTA
                ? driverRepository.findById(user.getId()).orElse(null)
                : null;

        // Entidade gerenciada: o flush da transação persiste, sem query extra.
        if (cidadeChanged && driver != null && driver.isOnline()) {
            driver.setOnline(false);
            driver.setLastSeenAt(Instant.now());
            log.info("Driver {} → offline (trocou de cidade {} -> {})",
                    user.getId(), auth.cidade(), cidade);
        }

        log.info("Perfil do usuário {} atualizado (cidade {} -> {})",
                user.getId(), auth.cidade(), cidade);

        return new UpdateProfileResponse(UserProfileResponse.from(user, driver), token, tokenExpiresAt);
    }

    /**
     * Troca a senha, revoga todos os tokens anteriores e devolve um novo para
     * esta sessao continuar. Tambem e o caminho para sair da senha temporaria.
     */
    @Transactional
    public AuthResponse changePassword(AuthenticatedUser auth, String currentPassword, String newPassword) {
        User user = userRepository.findById(auth.userId())
                .orElseThrow(UserNotFoundException::new);
        // Conta criada por login social nao tem senha atual para conferir.
        // Aqui o usuario ja esta autenticado, entao a mensagem especifica nao
        // vaza nada — e sem ela o erro seria "senha atual incorreta", que
        // manda o usuario procurar uma senha que nunca existiu.
        if (user.getPasswordHash() == null) {
            throw new PasswordNotSetException();
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTokensValidAfter(Instant.now());
        events.publishEvent(new AccountAccessRevokedEvent(user.getId()));
        log.info("Senha alterada pelo próprio usuário {} (sessões anteriores revogadas)", user.getId());
        return AuthResponse.of(user, jwtService.generate(user));
    }

    /**
     * Reset de senha pelo admin — alternativa ao {@link #resetPasswordWithPhone}
     * para quem perdeu o acesso ao número. A senha temporária retorna em claro uma única vez e
     * o admin repassa ao usuário por canal externo.
     */
    @Transactional
    public AdminResetPasswordResponse resetPassword(UUID targetUserId, UUID adminId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotResetAdminPasswordException();
        }
        String temporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        // Temporaria so serve para escolher outra; e quem tinha a senha antiga perde a sessao.
        user.setMustChangePassword(true);
        user.setTokensValidAfter(Instant.now());
        events.publishEvent(new AccountAccessRevokedEvent(user.getId()));
        auditLog.record(adminId, AuditAction.USER_PASSWORD_RESET, "USER", targetUserId, null);
        log.warn("Senha do usuário {} resetada pelo admin {}", targetUserId, adminId);
        return new AdminResetPasswordResponse(user.getId(), user.getEmail(), temporaryPassword);
    }

    /**
     * Esqueci minha senha, sem sessao e sem admin: o usuario prova a posse do
     * numero da conta num desafio {@code PASSWORD_RESET} do WhatsApp e escolhe
     * a senha nova. Mesmo efeito da troca de senha: sessoes anteriores caem e o
     * bloqueio por tentativas e zerado. Conta so-social ganha senha (vira dual),
     * como no reset pelo admin. Nao devolve token: o app segue para o login.
     */
    @Transactional
    public void resetPasswordWithPhone(String verificationToken, String newPassword) {
        String phone = phoneVerificationService.consumePasswordResetPhone(verificationToken);
        User user = userRepository.findByPhone(phone).orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotResetAdminPasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTokensValidAfter(Instant.now());
        events.publishEvent(new AccountAccessRevokedEvent(user.getId()));
        loginAttemptService.recordSuccess(user.getEmail());
        log.info("Senha do usuario {} redefinida via WhatsApp (sessoes anteriores revogadas)", user.getId());
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
