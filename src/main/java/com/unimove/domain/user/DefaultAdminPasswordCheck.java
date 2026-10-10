package com.unimove.domain.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alerta no boot se algum ADMIN ainda usa a senha do seed (V2), que esta
 * escrita no proprio arquivo da migration e portanto e publica para quem tem
 * acesso ao repositorio. A migration nao pode ser editada (Flyway valida o
 * checksum), entao a rotacao e operacional: logar como admin e usar
 * PUT /users/me/password.
 *
 * <p>So alerta, nao derruba o boot: impedir a subida da API por isso tiraria
 * do ar justamente o caminho de correcao.
 */
@Component
class DefaultAdminPasswordCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminPasswordCheck.class);
    static final String SEED_PASSWORD = "ChangeMe123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    DefaultAdminPasswordCheck(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        userRepository.findByRole(Role.ADMIN).stream()
                .filter(admin -> admin.getPasswordHash() != null
                        && passwordEncoder.matches(SEED_PASSWORD, admin.getPasswordHash()))
                .forEach(admin -> log.error(
                        "SEGURANCA: o admin {} ainda usa a senha padrao do seed (V2). "
                                + "Troque-a ja via PUT /users/me/password.", admin.getId()));
    }
}
