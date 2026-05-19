package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.shared.security.JwtService;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       DriverRepository driverRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setName(req.name().trim());
        user.setPhone(req.phone());
        user.setRole(req.role());
        user.setCidade(CityNormalizer.normalize(req.cidade()));
        userRepository.save(user);

        if (req.role() == Role.MOTORISTA) {
            Driver driver = new Driver();
            driver.setUser(user);
            driver.setApproved(false);
            driver.setOnline(false);
            driver.setVehicleType(req.vehicleType());
            driver.setVehiclePlate(req.vehiclePlate().toUpperCase());
            driverRepository.save(driver);
        }

        log.info("Novo cadastro: userId={}, role={}, cidade={}", user.getId(), user.getRole(), user.getCidade());
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Credenciais inválidas");
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
