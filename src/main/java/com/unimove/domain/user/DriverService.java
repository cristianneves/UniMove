package com.unimove.domain.user;

import com.unimove.domain.user.dto.DriverPublicInfo;
import com.unimove.domain.user.dto.DriverStatusResponse;
import com.unimove.domain.user.dto.PendingDriverItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final UserAccountService userAccountService;

    public DriverService(DriverRepository driverRepository,
                         UserRepository userRepository,
                         UserAccountService userAccountService) {
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
    }

    @Transactional(readOnly = true)
    public Optional<DriverPublicInfo> findPublicInfo(UUID userId) {
        Driver d = driverRepository.findById(userId).orElse(null);
        if (d == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(u -> new DriverPublicInfo(
                UserAccountService.firstName(u.getName()),
                d.getVehicleType(),
                d.getVehiclePlate(),
                u.getRatingAvg(),
                u.getRatingCount()
        ));
    }

    @Transactional
    public DriverStatusResponse goOnline(UUID userId) {
        userAccountService.requireActive(userId);
        Driver d = driverRepository.findById(userId)
                .orElseThrow(DriverNotFoundException::new);
        if (!d.isApproved()) {
            throw new DriverNotApprovedException();
        }
        d.setOnline(true);
        d.setLastSeenAt(Instant.now());
        log.info("Driver {} → online", userId);
        return DriverStatusResponse.from(d);
    }

    @Transactional
    public DriverStatusResponse goOffline(UUID userId) {
        Driver d = driverRepository.findById(userId)
                .orElseThrow(DriverNotFoundException::new);
        d.setOnline(false);
        d.setLastSeenAt(Instant.now());
        log.info("Driver {} → offline", userId);
        return DriverStatusResponse.from(d);
    }

    @Transactional
    public void touchLastSeenAt(UUID userId) {
        driverRepository.updateLastSeenAt(userId, Instant.now());
    }

    /**
     * Marca offline motoristas sem atividade desde o cutoff. Chamado pelo
     * {@code DriverAutoOfflineScheduler} — evita mural "fantasma" com motorista
     * que fechou o app sem tocar em "ficar offline".
     */
    @Transactional
    public int markStaleDriversOffline(Instant cutoff) {
        return driverRepository.markStaleOnlineDriversOffline(cutoff);
    }

    @Transactional(readOnly = true)
    public void assertCanAcceptRides(UUID userId) {
        Driver d = driverRepository.findById(userId)
                .orElseThrow(DriverNotFoundException::new);
        if (!d.isApproved()) {
            throw new DriverNotApprovedException();
        }
        if (!d.isOnline()) {
            throw new DriverOfflineException();
        }
    }

    @Transactional(readOnly = true)
    public VehicleType getVehicleType(UUID userId) {
        return driverRepository.findById(userId)
                .map(Driver::getVehicleType)
                .orElseThrow(DriverNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<PendingDriverItem> listPending() {
        return driverRepository.findPending();
    }

    @Transactional
    public DriverStatusResponse approve(UUID userId) {
        Driver d = driverRepository.findById(userId)
                .orElseThrow(DriverNotFoundException::new);
        d.setApproved(true);
        log.info("Driver {} aprovado pelo admin", userId);
        return DriverStatusResponse.from(d);
    }
}
