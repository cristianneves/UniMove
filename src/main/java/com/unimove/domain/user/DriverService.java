package com.unimove.domain.user;

import com.unimove.domain.user.dto.DriverStatusResponse;
import com.unimove.domain.user.dto.PendingDriverItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public DriverStatusResponse goOnline(UUID userId) {
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
