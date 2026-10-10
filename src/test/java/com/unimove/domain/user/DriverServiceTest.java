package com.unimove.domain.user;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.user.document.DocumentationStatus;
import com.unimove.domain.user.document.DriverDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock DriverRepository driverRepository;
    @Mock UserRepository userRepository;
    @Mock UserAccountService userAccountService;
    @Mock AdminAuditLog auditLog;
    @Mock DriverDocumentService documentService;
    @InjectMocks DriverService service;

    private final UUID driverId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = new Driver();
        driver.setId(driverId);
        driver.setVehicleType(VehicleType.CARRO);
        lenient().when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("goOnline: aprovado e CNH valida → online")
    void goOnlineHappyPath() {
        driver.setApproved(true);
        driver.setCnhExpiresOn(LocalDate.now().plusYears(1));

        assertThat(service.goOnline(driverId).online()).isTrue();
    }

    @Test
    @DisplayName("goOnline: nao aprovado → 403")
    void goOnlineNotApproved() {
        assertThatThrownBy(() -> service.goOnline(driverId)).isInstanceOf(DriverNotApprovedException.class);
    }

    @Test
    @DisplayName("goOnline: aprovado com CNH vencida → 403 e continua offline")
    void goOnlineExpiredCnh() {
        driver.setApproved(true);
        driver.setCnhExpiresOn(LocalDate.now().minusDays(2));

        assertThatThrownBy(() -> service.goOnline(driverId)).isInstanceOf(DriverDocumentationInvalidException.class);
        assertThat(driver.isOnline()).isFalse();
    }

    @Test
    @DisplayName("aprovado antes do RF02 (sem CNH cadastrada) nao opera")
    void legacyApprovedWithoutCnhCannotOperate() {
        driver.setApproved(true);
        driver.setOnline(true);

        assertThatThrownBy(() -> service.assertCanAcceptRides(driverId))
                .isInstanceOf(DriverDocumentationInvalidException.class);
    }

    @Test
    @DisplayName("pendentes: paginado e com a situacao documental de cada motorista")
    void pendingIsPagedWithDocumentationStatus() {
        User user = new User();
        user.setId(driverId);
        user.setName("Joao");
        driver.setUser(user);
        Pageable page = PageRequest.of(0, 20);
        when(driverRepository.findPending(page))
                .thenReturn(new PageImpl<>(List.of(driver), page, 1));
        when(documentService.statusOf(driver))
                .thenReturn(DocumentationStatus.PENDING_REVIEW);

        var result = service.listPending(page);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Joao");
        assertThat(result.getContent().get(0).documentationStatus())
                .isEqualTo(DocumentationStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("approve: documentacao aprovada → aprova e audita")
    void approveWithDocumentation() {
        assertThat(service.approve(driverId, adminId).approved()).isTrue();

        verify(documentService).assertReadyForApproval(driverId);
        verify(auditLog).record(adminId, AuditAction.DRIVER_APPROVED, "USER", driverId, null);
    }

    @Test
    @DisplayName("approve: documentacao pendente → erro, motorista segue nao aprovado e nada e auditado")
    void approveBlockedByDocumentation() {
        doThrow(new IllegalStateException("pendente")).when(documentService).assertReadyForApproval(driverId);

        assertThatThrownBy(() -> service.approve(driverId, adminId)).hasMessage("pendente");
        assertThat(driver.isApproved()).isFalse();
        verifyNoInteractions(auditLog);
    }
}
