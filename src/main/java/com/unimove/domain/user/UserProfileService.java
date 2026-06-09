package com.unimove.domain.user;

import com.unimove.domain.user.dto.AdminResetPasswordResponse;
import com.unimove.domain.user.dto.UpdateProfileRequest;
import com.unimove.domain.user.dto.UpdateProfileResponse;
import com.unimove.domain.user.dto.UserProfileResponse;
import com.unimove.shared.security.AuthenticatedUser;
import com.unimove.shared.security.JwtService;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final SecureRandom random = new SecureRandom();

    public UserProfileService(UserRepository userRepository,
                              DriverRepository driverRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
     * Atualiza nome, telefone e cidade. E-mail (login) e role são imutáveis.
     * Como o JWT carrega a claim {@code cidade}, mudança de cidade reemite o
     * token — sem isso, mural e criação de corrida continuariam usando a
     * cidade antiga até o re-login.
     */
    @Transactional
    public UpdateProfileResponse updateProfile(AuthenticatedUser auth, UpdateProfileRequest req) {
        User user = userRepository.findById(auth.userId())
                .orElseThrow(UserNotFoundException::new);

        String cidade = CityNormalizer.normalize(req.cidade());
        if (cidade == null || cidade.isEmpty()) {
            throw new InvalidCityException();
        }
        boolean cidadeChanged = !cidade.equals(user.getCidade());

        user.setName(req.name().trim());
        user.setPhone(trimToNull(req.phone()));
        user.setCidade(cidade);

        String token = null;
        Instant tokenExpiresAt = null;
        if (cidadeChanged) {
            JwtService.IssuedToken issued = jwtService.generate(user);
            token = issued.token();
            tokenExpiresAt = issued.expiresAt();
        }

        log.info("Perfil do usuário {} atualizado (cidade {} -> {})",
                user.getId(), auth.cidade(), cidade);

        Driver driver = user.getRole() == Role.MOTORISTA
                ? driverRepository.findById(user.getId()).orElse(null)
                : null;
        return new UpdateProfileResponse(UserProfileResponse.from(user, driver), token, tokenExpiresAt);
    }

    @Transactional
    public void changePassword(AuthenticatedUser auth, String currentPassword, String newPassword) {
        User user = userRepository.findById(auth.userId())
                .orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        log.info("Senha alterada pelo próprio usuário {}", user.getId());
    }

    /**
     * Reset de senha pelo admin — caminho de "esqueci minha senha" do MVP, sem
     * infra de e-mail/SMS. A senha temporária retorna em claro uma única vez e
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
        log.warn("Senha do usuário {} resetada pelo admin {}", targetUserId, adminId);
        return new AdminResetPasswordResponse(user.getId(), user.getEmail(), temporaryPassword);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
