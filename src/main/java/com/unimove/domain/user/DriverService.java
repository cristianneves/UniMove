package com.unimove.domain.user;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.user.document.DriverDocumentService;
import com.unimove.domain.user.dto.DriverPublicInfo;
import com.unimove.domain.user.dto.DriverStatusResponse;
import com.unimove.domain.user.dto.PendingDriverItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final AdminAuditLog auditLog;
    private final DriverDocumentService documentService;

    public DriverService(DriverRepository driverRepository,
                         UserRepository userRepository,
                         UserAccountService userAccountService,
                         AdminAuditLog auditLog,
                         DriverDocumentService documentService) {
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.auditLog = auditLog;
        this.documentService = documentService;
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
        assertOperational(d);
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

    /**
     * Aprovado pelo admin E com CNH valida hoje. A CNH vence com o motorista ja
     * aprovado; motorista aprovado antes do RF02 nao tem CNH cadastrada — os
     * dois casos ficam fora de operacao ate atualizar a documentacao.
     */
    private static void assertOperational(Driver d) {
        if (!d.isApproved()) {
            throw new DriverNotApprovedException();
        }
        if (!d.hasValidCnh(LocalDate.now(DriverDocumentService.BRAZIL))) {
            throw new DriverDocumentationInvalidException();
        }
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
        assertOperational(d);
        if (!d.isOnline()) {
            throw new DriverOfflineException();
        }
    }

    /** Motoristas online da cidade para o tipo de veiculo — oferta bruta consumida pelo surge. */
    @Transactional(readOnly = true)
    public long countOnline(String cidade, VehicleType vehicleType) {
        return driverRepository.countOnlineByCidadeAndVehicleType(cidade, vehicleType);
    }

    @Transactional(readOnly = true)
    public VehicleType getVehicleType(UUID userId) {
        return driverRepository.findById(userId)
                .map(Driver::getVehicleType)
                .orElseThrow(DriverNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<PendingDriverItem> listPending(Pageable pageable) {
        return driverRepository.findPending(pageable).map(d -> new PendingDriverItem(
                d.getUserId(), d.getUser().getName(), d.getUser().getEmail(), d.getUser().getPhone(),
                d.getUser().getCidade(), d.getVehicleType(), d.getVehiclePlate(), d.getUser().getCreatedAt(),
                documentService.statusOf(d)));
    }

    @Transactional
    public DriverStatusResponse approve(UUID userId, UUID adminId) {
        Driver d = driverRepository.findById(userId)
                .orElseThrow(DriverNotFoundException::new);
        documentService.assertReadyForApproval(userId);
        d.setApproved(true);
        auditLog.record(adminId, AuditAction.DRIVER_APPROVED, "USER", userId, null);
        log.info("Driver {} aprovado pelo admin {}", userId, adminId);
        return DriverStatusResponse.from(d);
    }
}
