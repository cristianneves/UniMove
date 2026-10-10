package com.unimove.domain.user.document;

import com.unimove.domain.audit.AdminAuditLog;
import com.unimove.domain.audit.AuditAction;
import com.unimove.domain.user.Driver;
import com.unimove.domain.user.DriverNotFoundException;
import com.unimove.domain.user.DriverRepository;
import com.unimove.domain.user.VehicleType;
import com.unimove.domain.user.document.dto.DocumentFileUrlResponse;
import com.unimove.domain.user.document.dto.DriverDocumentItem;
import com.unimove.domain.user.document.dto.DriverDocumentationResponse;
import com.unimove.domain.user.document.dto.DriverProfileRequest;
import com.unimove.shared.security.FieldCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverDocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final String VALID_CPF = "529.982.247-25";

    @Mock DriverRepository driverRepository;
    @Mock DriverDocumentRepository documentRepository;
    @Mock DocumentStorage storage;
    @Mock AdminAuditLog auditLog;
    @Mock FieldCrypto crypto;

    private DriverDocumentService service;
    private Driver driver;
    private UUID driverId;
    private final UUID adminId = UUID.randomUUID();
    private final List<DriverDocument> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DriverDocumentService(driverRepository, documentRepository, storage,
                new DocumentStorageProperties("https://x.supabase.co", "key", "driver-documents", 300, 1024),
                auditLog, crypto, Clock.fixed(NOW, ZoneOffset.UTC));
        driverId = UUID.randomUUID();
        driver = new Driver();
        driver.setId(driverId);
        driver.setVehicleType(VehicleType.CARRO);
        driver.setVehiclePlate("ABC1234");
        lenient().when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        lenient().when(documentRepository.findByDriverIdOrderByCreatedAtDesc(driverId)).thenReturn(history);
        lenient().when(documentRepository.save(any(DriverDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(crypto.fingerprint(anyString())).thenAnswer(inv -> "h:" + inv.getArgument(0));
    }

    // ------------------------------------------------------------------------
    // status consolidado
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("sem dados e sem documentos → INCOMPLETE listando o que falta")
    void emptyIsIncomplete() {
        DriverDocumentationResponse resp = service.getDocumentation(driverId);

        assertThat(resp.status()).isEqualTo(DocumentationStatus.INCOMPLETE);
        assertThat(resp.pending()).contains("Preencher CPF, CNH e dados do veículo", "Enviar CNH", "Enviar CRLV", "Enviar SELFIE");
    }

    @Test
    @DisplayName("tudo aprovado e CNH valida → APPROVED e libera a aprovacao do motorista")
    void allApprovedIsApproved() {
        completeProfile(TODAY.plusYears(1));
        allDocs(DriverDocumentStatus.APPROVED);

        assertThat(service.getDocumentation(driverId).status()).isEqualTo(DocumentationStatus.APPROVED);
        service.assertReadyForApproval(driverId);
    }

    @Test
    @DisplayName("documento reprovado → REJECTED com o motivo e bloqueia a aprovacao")
    void rejectedDocBlocksApproval() {
        completeProfile(TODAY.plusYears(1));
        allDocs(DriverDocumentStatus.APPROVED);
        history.get(1).setStatus(DriverDocumentStatus.REJECTED);
        history.get(1).setRejectionReason("Foto ilegível");

        DriverDocumentationResponse resp = service.getDocumentation(driverId);

        assertThat(resp.status()).isEqualTo(DocumentationStatus.REJECTED);
        assertThat(resp.pending()).anyMatch(p -> p.contains("Foto ilegível"));
        assertThatThrownBy(() -> service.assertReadyForApproval(driverId))
                .isInstanceOf(DriverDocumentationIncompleteException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    @DisplayName("documento enviado aguardando analise → PENDING_REVIEW")
    void submittedIsPendingReview() {
        completeProfile(TODAY.plusYears(1));
        allDocs(DriverDocumentStatus.SUBMITTED);

        assertThat(service.getDocumentation(driverId).status()).isEqualTo(DocumentationStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("tudo aprovado mas CNH vencida → EXPIRED")
    void expiredCnh() {
        completeProfile(TODAY.minusDays(1));
        allDocs(DriverDocumentStatus.APPROVED);

        assertThat(service.getDocumentation(driverId).status()).isEqualTo(DocumentationStatus.EXPIRED);
    }

    @Test
    @DisplayName("historico: vigente e o envio mais recente de cada tipo, antigos ficam no historico")
    void currentIsNewestPerType() {
        completeProfile(TODAY.plusYears(1));
        allDocs(DriverDocumentStatus.APPROVED);
        DriverDocument oldRejected = doc(DriverDocumentType.CNH, DriverDocumentStatus.REJECTED);
        history.add(oldRejected);

        DriverDocumentationResponse resp = service.getDocumentation(driverId);

        assertThat(resp.current()).hasSize(3).allMatch(d -> d.status() == DriverDocumentStatus.APPROVED);
        assertThat(resp.history()).hasSize(4);
    }

    // ------------------------------------------------------------------------
    // dados declarados
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("updateProfile normaliza CPF e placa e grava tudo")
    void updateProfileHappyPath() {
        DriverDocumentationResponse resp = service.updateProfile(driverId, profile("B", TODAY.plusYears(2), "abc-1d23"));

        assertThat(driver.getCpf()).isEqualTo("52998224725");
        assertThat(driver.getCpfHash()).isEqualTo("h:52998224725");
        assertThat(driver.getCnhNumberHash()).isEqualTo("h:12345678901");
        assertThat(driver.getVehiclePlate()).isEqualTo("ABC1D23");
        assertThat(driver.hasCompleteProfile()).isTrue();
        assertThat(resp.profile().cnhCategory()).isEqualTo("B");
    }

    @Test
    @DisplayName("updateProfile com cadastro aprovado e CNH valida → 409 travado")
    void updateProfileLockedWhenApproved() {
        completeProfile(TODAY.plusYears(1));
        driver.setApproved(true);

        assertThatThrownBy(() -> service.updateProfile(driverId, profile("B", TODAY.plusYears(2), "ABC1234")))
                .isInstanceOf(DriverProfileLockedException.class);
    }

    @Test
    @DisplayName("aprovado com CNH vencida pode atualizar e volta para analise (offline)")
    void updateProfileWithExpiredCnhResetsApproval() {
        completeProfile(TODAY.minusDays(3));
        driver.setApproved(true);
        driver.setOnline(true);

        service.updateProfile(driverId, profile("B", TODAY.plusYears(5), "ABC1234"));

        assertThat(driver.isApproved()).isFalse();
        assertThat(driver.isOnline()).isFalse();
        assertThat(driver.getCnhExpiresOn()).isEqualTo(TODAY.plusYears(5));
    }

    @Test
    @DisplayName("categoria da CNH incompativel com o veiculo → 400")
    void categoryMustFitVehicle() {
        driver.setVehicleType(VehicleType.MOTO);

        assertThatThrownBy(() -> service.updateProfile(driverId, profile("B", TODAY.plusYears(1), "ABC1234")))
                .isInstanceOf(InvalidDriverProfileException.class)
                .hasMessageContaining("categoria A");
    }

    @Test
    @DisplayName("CNH vencida no cadastro → 400")
    void expiredCnhRejectedOnProfile() {
        assertThatThrownBy(() -> service.updateProfile(driverId, profile("B", TODAY.minusDays(1), "ABC1234")))
                .isInstanceOf(InvalidDriverProfileException.class);
    }

    @Test
    @DisplayName("CPF ja usado por outro motorista → 409")
    void duplicateCpf() {
        when(driverRepository.existsByCpfHashAndIdNot("h:52998224725", driverId)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(driverId, profile("B", TODAY.plusYears(1), "ABC1234")))
                .isInstanceOf(DriverIdentityInUseException.class);
    }

    @Test
    @DisplayName("mudar dados da CNH invalida a CNH ja aprovada (precisa reenviar)")
    void changingCnhInvalidatesApprovedCnh() {
        completeProfile(TODAY.plusYears(1));
        DriverDocument cnh = doc(DriverDocumentType.CNH, DriverDocumentStatus.APPROVED);
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, DriverDocumentType.CNH))
                .thenReturn(Optional.of(cnh));

        service.updateProfile(driverId, profile("AB", TODAY.plusYears(1), "ABC1234"));

        assertThat(cnh.getStatus()).isEqualTo(DriverDocumentStatus.REJECTED);
        assertThat(cnh.getRejectionReason()).contains("CNH");
    }

    // ------------------------------------------------------------------------
    // upload
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("upload grava no storage privado com tipo detectado e registra SUBMITTED")
    void uploadHappyPath() {
        DriverDocumentItem item = service.upload(driverId, DriverDocumentType.CNH, file(DocumentFileInspectorTest.JPEG));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).put(path.capture(), any(), eq("image/jpeg"));
        assertThat(path.getValue()).startsWith(driverId + "/cnh/").endsWith(".jpg");
        assertThat(item.status()).isEqualTo(DriverDocumentStatus.SUBMITTED);
        assertThat(item.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("upload com documento aguardando analise → 409 sem tocar no storage")
    void uploadBlockedWhileSubmitted() {
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, DriverDocumentType.CRLV))
                .thenReturn(Optional.of(doc(DriverDocumentType.CRLV, DriverDocumentStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CRLV, file(DocumentFileInspectorTest.PDF)))
                .isInstanceOf(DocumentUploadNotAllowedException.class);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("reenvio de documento reprovado e aceito")
    void reuploadAfterRejection() {
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, DriverDocumentType.CRLV))
                .thenReturn(Optional.of(doc(DriverDocumentType.CRLV, DriverDocumentStatus.REJECTED)));

        assertThat(service.upload(driverId, DriverDocumentType.CRLV, file(DocumentFileInspectorTest.PDF)).status())
                .isEqualTo(DriverDocumentStatus.SUBMITTED);
    }

    @Test
    @DisplayName("CNH aprovada pode ser reenviada quando vencida")
    void reuploadExpiredCnh() {
        completeProfile(TODAY.minusDays(1));
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, DriverDocumentType.CNH))
                .thenReturn(Optional.of(doc(DriverDocumentType.CNH, DriverDocumentStatus.APPROVED)));

        assertThat(service.upload(driverId, DriverDocumentType.CNH, file(DocumentFileInspectorTest.PNG)).status())
                .isEqualTo(DriverDocumentStatus.SUBMITTED);
    }

    @Test
    @DisplayName("CNH vencida com envio ja em analise nao duplica o envio → 409")
    void expiredCnhDoesNotDuplicateSubmitted() {
        completeProfile(TODAY.minusDays(1));
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, DriverDocumentType.CNH))
                .thenReturn(Optional.of(doc(DriverDocumentType.CNH, DriverDocumentStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file(DocumentFileInspectorTest.JPEG)))
                .isInstanceOf(DocumentUploadNotAllowedException.class);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("admin ver a documentacao (CPF/CNH) fica auditado")
    void adminViewIsAudited() {
        service.getDocumentationForAdmin(driverId, adminId);

        verify(auditLog).record(adminId, AuditAction.DRIVER_DOCUMENTATION_VIEWED, "USER", driverId, null);
    }

    @Test
    @DisplayName("statusOf calcula a situacao de um motorista ja carregado")
    void statusOfLoadedDriver() {
        completeProfile(TODAY.plusYears(1));
        allDocs(DriverDocumentStatus.APPROVED);

        assertThat(service.statusOf(driver)).isEqualTo(DocumentationStatus.APPROVED);
    }

    @Test
    @DisplayName("arquivo de tipo nao suportado → 400 sem tocar no storage")
    void uploadRejectsUnknownType() {
        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file("GIF89a".getBytes())))
                .isInstanceOf(InvalidDocumentFileException.class);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("selfie em PDF → 400")
    void selfieMustBeImage() {
        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.SELFIE, file(DocumentFileInspectorTest.PDF)))
                .isInstanceOf(InvalidDocumentFileException.class)
                .hasMessageContaining("foto");
    }

    @Test
    @DisplayName("arquivo acima do teto → 400")
    void uploadTooLarge() {
        byte[] big = new byte[2048];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;

        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file(big)))
                .isInstanceOf(InvalidDocumentFileException.class)
                .hasMessageContaining("maior");
    }

    @Test
    @DisplayName("arquivo vazio → 400")
    void uploadEmpty() {
        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file(new byte[0])))
                .isInstanceOf(InvalidDocumentFileException.class);
    }

    @Test
    @DisplayName("falha ao gravar no banco apaga o arquivo recem-enviado (sem orfao)")
    void uploadCleansStorageWhenSaveFails() {
        when(documentRepository.save(any(DriverDocument.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file(DocumentFileInspectorTest.JPEG)))
                .hasMessage("db down");
        ArgumentCaptor<String> put = ArgumentCaptor.forClass(String.class);
        verify(storage).put(put.capture(), any(), anyString());
        verify(storage).delete(List.of(put.getValue()));
    }

    @Test
    @DisplayName("envio concorrente barrado pelo indice unico → 409 e arquivo removido")
    void concurrentDuplicateUploadIsConflict() {
        when(documentRepository.save(any(DriverDocument.class))).thenThrow(new DataIntegrityViolationException(
                "dup", new RuntimeException("duplicate key value violates unique constraint \"ux_driver_documents_one_submitted\"")));

        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file(DocumentFileInspectorTest.JPEG)))
                .isInstanceOf(DocumentUploadNotAllowedException.class)
                .hasMessageContaining("aguarda análise");
        ArgumentCaptor<String> put = ArgumentCaptor.forClass(String.class);
        verify(storage).put(put.capture(), any(), anyString());
        verify(storage).delete(List.of(put.getValue()));
    }

    @Test
    @DisplayName("outra violacao de integridade segue para o handler global")
    void otherIntegrityViolationPropagates() {
        when(documentRepository.save(any(DriverDocument.class))).thenThrow(new DataIntegrityViolationException(
                "fk", new RuntimeException("violates foreign key constraint")));

        assertThatThrownBy(() -> service.upload(driverId, DriverDocumentType.CNH, file(DocumentFileInspectorTest.JPEG)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("motorista inexistente → 404")
    void uploadUnknownDriver() {
        UUID other = UUID.randomUUID();
        when(driverRepository.findById(other)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(other, DriverDocumentType.CNH, file(DocumentFileInspectorTest.JPEG)))
                .isInstanceOf(DriverNotFoundException.class);
    }

    // ------------------------------------------------------------------------
    // revisao do admin
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("approve: vigente SUBMITTED → APPROVED com revisor e auditoria")
    void approveDocument() {
        DriverDocument d = currentDoc(DriverDocumentType.CNH, DriverDocumentStatus.SUBMITTED);

        DriverDocumentItem item = service.approve(driverId, d.getId(), adminId);

        assertThat(item.status()).isEqualTo(DriverDocumentStatus.APPROVED);
        assertThat(d.getReviewedByAdminId()).isEqualTo(adminId);
        verify(auditLog).record(eq(adminId), eq(AuditAction.DRIVER_DOCUMENT_APPROVED), eq("DRIVER_DOCUMENT"),
                eq(d.getId()), anyString());
    }

    @Test
    @DisplayName("approve de envio antigo (nao vigente) → 409")
    void approveOnlyCurrent() {
        DriverDocument old = doc(DriverDocumentType.CNH, DriverDocumentStatus.SUBMITTED);
        DriverDocument newer = doc(DriverDocumentType.CNH, DriverDocumentStatus.SUBMITTED);
        when(documentRepository.findByIdAndDriverId(old.getId(), driverId)).thenReturn(Optional.of(old));
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, DriverDocumentType.CNH))
                .thenReturn(Optional.of(newer));

        assertThatThrownBy(() -> service.approve(driverId, old.getId(), adminId))
                .isInstanceOf(DocumentReviewNotAllowedException.class);
    }

    @Test
    @DisplayName("approve de documento ja aprovado → 409")
    void approveTwice() {
        DriverDocument d = currentDoc(DriverDocumentType.CNH, DriverDocumentStatus.APPROVED);

        assertThatThrownBy(() -> service.approve(driverId, d.getId(), adminId))
                .isInstanceOf(DocumentReviewNotAllowedException.class);
    }

    @Test
    @DisplayName("reject de documento de motorista aprovado revoga a aprovacao e tira de operacao")
    void rejectRevokesApproval() {
        DriverDocument d = currentDoc(DriverDocumentType.CRLV, DriverDocumentStatus.APPROVED);
        driver.setApproved(true);
        driver.setOnline(true);

        DriverDocumentItem item = service.reject(driverId, d.getId(), adminId, "  CRLV vencido  ");

        assertThat(item.status()).isEqualTo(DriverDocumentStatus.REJECTED);
        assertThat(item.rejectionReason()).isEqualTo("CRLV vencido");
        assertThat(driver.isApproved()).isFalse();
        assertThat(driver.isOnline()).isFalse();
        verify(auditLog).record(eq(adminId), eq(AuditAction.DRIVER_DOCUMENT_REJECTED), eq("DRIVER_DOCUMENT"),
                eq(d.getId()), anyString());
    }

    @Test
    @DisplayName("reject de documento ja reprovado → 409")
    void rejectTwice() {
        DriverDocument d = currentDoc(DriverDocumentType.CRLV, DriverDocumentStatus.REJECTED);

        assertThatThrownBy(() -> service.reject(driverId, d.getId(), adminId, "x"))
                .isInstanceOf(DocumentReviewNotAllowedException.class);
    }

    @Test
    @DisplayName("fileUrl audita o acesso e devolve URL assinada curta")
    void fileUrlIsAudited() {
        DriverDocument d = doc(DriverDocumentType.SELFIE, DriverDocumentStatus.SUBMITTED);
        when(documentRepository.findByIdAndDriverId(d.getId(), driverId)).thenReturn(Optional.of(d));
        when(storage.signedUrl(d.getStoragePath(), Duration.ofSeconds(300)))
                .thenReturn(new DocumentStorage.SignedUrl("https://signed", NOW.plusSeconds(300)));

        DocumentFileUrlResponse resp = service.fileUrl(driverId, d.getId(), adminId);

        assertThat(resp.url()).isEqualTo("https://signed");
        verify(auditLog).record(eq(adminId), eq(AuditAction.DRIVER_DOCUMENT_VIEWED), eq("DRIVER_DOCUMENT"),
                eq(d.getId()), anyString());
    }

    @Test
    @DisplayName("fileUrl de documento de outro motorista → 404 sem auditar nem assinar")
    void fileUrlOtherDriver() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findByIdAndDriverId(docId, driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fileUrl(driverId, docId, adminId))
                .isInstanceOf(DriverDocumentNotFoundException.class);
        verifyNoInteractions(auditLog, storage);
    }

    @Test
    @DisplayName("purgeFiles apaga do storage e zera os caminhos, mantendo o registro")
    void purgeFiles() {
        allDocs(DriverDocumentStatus.APPROVED);
        List<String> paths = history.stream().map(DriverDocument::getStoragePath).toList();
        when(documentRepository.findStoragePaths(driverId)).thenReturn(paths);

        assertThat(service.purgeFiles(driverId)).isEqualTo(3);

        verify(storage).delete(paths);
        assertThat(history).allMatch(d -> d.getStoragePath() == null);
    }

    @Test
    @DisplayName("retencao: apaga do storage os arquivos obsoletos e zera o caminho, mantendo o registro")
    void purgeObsoleteFiles() {
        DriverDocument rejected = doc(DriverDocumentType.CRLV, DriverDocumentStatus.REJECTED);
        String path = rejected.getStoragePath();
        when(documentRepository.findObsoleteWithFile(any(), any())).thenReturn(List.of(rejected));

        assertThat(service.purgeObsoleteFiles(NOW)).isEqualTo(1);

        verify(storage).delete(List.of(path));
        assertThat(rejected.getStoragePath()).isNull();
        assertThat(rejected.getStatus()).isEqualTo(DriverDocumentStatus.REJECTED);
    }

    @Test
    @DisplayName("retencao sem obsoletos nao chama o storage")
    void purgeObsoleteNothing() {
        when(documentRepository.findObsoleteWithFile(any(), any())).thenReturn(List.of());

        assertThat(service.purgeObsoleteFiles(NOW)).isZero();
        verify(storage, never()).delete(any());
    }

    @Test
    @DisplayName("purgeFiles sem arquivos nao chama o storage")
    void purgeNothing() {
        when(documentRepository.findStoragePaths(driverId)).thenReturn(List.of());

        assertThat(service.purgeFiles(driverId)).isZero();
        verify(storage, never()).delete(any());
    }

    // ------------------------------------------------------------------------
    // builders
    // ------------------------------------------------------------------------

    private void completeProfile(LocalDate cnhExpiresOn) {
        driver.setCpf("52998224725");
        driver.setCnhNumber("12345678901");
        driver.setCnhCategory("B");
        driver.setCnhExpiresOn(cnhExpiresOn);
        driver.setVehicleBrand("Fiat");
        driver.setVehicleModel("Uno");
        driver.setVehicleYear((short) 2015);
        driver.setVehicleColor("Prata");
    }

    private void allDocs(DriverDocumentStatus status) {
        for (DriverDocumentType t : DriverDocumentType.values()) {
            history.add(doc(t, status));
        }
    }

    private DriverDocument doc(DriverDocumentType type, DriverDocumentStatus status) {
        DriverDocument d = new DriverDocument();
        d.setId(UUID.randomUUID());
        d.setDriverId(driverId);
        d.setType(type);
        d.setStatus(status);
        d.setStoragePath(driverId + "/" + type.name().toLowerCase() + "/" + d.getId() + ".jpg");
        d.setContentType("image/jpeg");
        d.setSizeBytes(100);
        d.setCreatedAt(NOW);
        return d;
    }

    private DriverDocument currentDoc(DriverDocumentType type, DriverDocumentStatus status) {
        DriverDocument d = doc(type, status);
        when(documentRepository.findByIdAndDriverId(d.getId(), driverId)).thenReturn(Optional.of(d));
        when(documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, type))
                .thenReturn(Optional.of(d));
        return d;
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "doc.bin", "application/octet-stream", content);
    }

    private static DriverProfileRequest profile(String category, LocalDate expiresOn, String plate) {
        return new DriverProfileRequest(VALID_CPF, "12345678901", category, expiresOn,
                "Fiat", "Uno", 2015, "Prata", plate);
    }
}
