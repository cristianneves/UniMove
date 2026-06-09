package com.unimove.domain.user;

import com.unimove.domain.user.dto.AdminResetPasswordResponse;
import com.unimove.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    // Sem caracteres ambíguos (0/O, 1/l/I) — a senha temporária é ditada por telefone/WhatsApp.
    private static final String TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public UserProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
