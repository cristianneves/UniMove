package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.domain.verification.PhoneVerificationService;
import com.unimove.shared.security.JwtService;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final PhoneVerificationService phoneVerificationService;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       DriverRepository driverRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService,
                       PhoneVerificationService phoneVerificationService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.phoneVerificationService = phoneVerificationService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            log.warn("Tentativa de auto-cadastro como ADMIN bloqueada: email={}", req.email());
            throw new RoleNotSelfAssignableException();
        }

        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        String cidade = CityNormalizer.normalize(req.cidade());
        if (cidade.isEmpty()) {
            throw new InvalidCityException();
        }

        // O telefone vem do desafio verificado (wa_id entregue pela Meta), nunca
        // do formulario. Roda dentro desta transacao de proposito: se o cadastro
        // falhar adiante, o rollback devolve o token e o usuario corrige o
        // formulario sem precisar verificar de novo.
        String verifiedPhone = phoneVerificationService.consumeVerifiedPhone(req.verificationToken());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setName(req.name().trim());
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

        log.info("Novo cadastro: userId={}, role={}, cidade={}", user.getId(), user.getRole(), user.getCidade());
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        loginAttemptService.assertNotLocked(email);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new BadCredentialsException("Credenciais inválidas");
        }
        loginAttemptService.recordSuccess(email);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserSuspendedException();
        }
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        JwtService.IssuedToken issued = jwtService.generate(user);
        return new AuthResponse(
                issued.token(),
                user.getId(),
                user.getRole(),
                user.getCidade(),
                issued.expiresAt()
        );
    }
}
