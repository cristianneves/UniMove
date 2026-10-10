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
import com.unimove.domain.user.document.dto.PendingDocumentItem;
import com.unimove.shared.security.FieldCrypto;
import com.unimove.shared.util.Cpf;
import com.unimove.shared.util.VehiclePlate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cadastro documental do motorista (RF02): dados declarados, envio privado
 * dos arquivos e revisao do admin com motivo.
 *
 * <p>Regras centrais:
 * <ul>
 *   <li>Cada envio e uma linha nova; o vigente de cada tipo e o mais recente.
 *       Reprovar nao apaga nada — o historico da analise fica.</li>
 *   <li>Reenvio so do que foi reprovado (ou da CNH vencida).</li>
 *   <li>Reprovar documento de motorista aprovado revoga a aprovacao e o tira
 *       de operacao: documentacao invalida nao opera.</li>
 *   <li>Cadastro aprovado fica travado no app; com a CNH vencida destrava para
 *       o motorista atualizar, e a mudanca volta para analise.</li>
 * </ul>
 */
@Service
public class DriverDocumentService {

    private static final Logger log = LoggerFactory.getLogger(DriverDocumentService.class);

    /** Validade de CNH e data civil brasileira. */
    private static final String ALREADY_SUBMITTED = "Este documento já foi enviado e aguarda análise.";

    public static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository documentRepository;
    private final DocumentStorage storage;
    private final DocumentStorageProperties storageProps;
    private final AdminAuditLog auditLog;
    private final FieldCrypto crypto;
    private final Clock clock;

    public DriverDocumentService(DriverRepository driverRepository,
                                 DriverDocumentRepository documentRepository,
                                 DocumentStorage storage,
                                 DocumentStorageProperties storageProps,
                                 AdminAuditLog auditLog,
                                 FieldCrypto crypto,
                                 Clock clock) {
        this.driverRepository = driverRepository;
        this.documentRepository = documentRepository;
        this.storage = storage;
        this.storageProps = storageProps;
        this.auditLog = auditLog;
        this.crypto = crypto;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DriverDocumentationResponse getDocumentation(UUID driverId) {
        Driver driver = loadDriver(driverId);
        List<DriverDocument> history = documentRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
        return toResponse(driver, history);
    }

    /** Situacao consolidada de um motorista ja carregado (fila de pendentes do admin). */
    @Transactional(readOnly = true)
    public DocumentationStatus statusOf(Driver driver) {
        return evaluate(driver, currentByType(
                documentRepository.findByDriverIdOrderByCreatedAtDesc(driver.getUserId())), new ArrayList<>());
    }

    /**
     * Mesma visao do motorista, aberta pelo admin: mostra CPF e CNH, entao o
     * acesso fica na trilha de auditoria.
     */
    @Transactional
    public DriverDocumentationResponse getDocumentationForAdmin(UUID driverId, UUID adminId) {
        DriverDocumentationResponse resp = getDocumentation(driverId);
        auditLog.record(adminId, AuditAction.DRIVER_DOCUMENTATION_VIEWED, "USER", driverId, null);
        return resp;
    }

    /** Fila de analise: envios aguardando o admin, mais antigos primeiro. */
    @Transactional(readOnly = true)
    public Page<PendingDocumentItem> pendingDocuments(Pageable pageable) {
        return documentRepository.findPendingReview(pageable);
    }

    /** Lanca 409 com o que falta se a documentacao nao permite aprovar o motorista. */
    @Transactional(readOnly = true)
    public void assertReadyForApproval(UUID driverId) {
        Driver driver = loadDriver(driverId);
        List<String> pending = new ArrayList<>();
        DocumentationStatus status = evaluate(driver, currentByType(
                documentRepository.findByDriverIdOrderByCreatedAtDesc(driverId)), pending);
        if (status != DocumentationStatus.APPROVED) {
            throw new DriverDocumentationIncompleteException(status, pending);
        }
    }

    // ------------------------------------------------------------------------
    // Motorista
    // ------------------------------------------------------------------------

    @Transactional
    public DriverDocumentationResponse updateProfile(UUID driverId, DriverProfileRequest req) {
        Driver driver = loadDriver(driverId);
        LocalDate today = today();
        boolean expiredCnh = driver.getCnhExpiresOn() != null && !driver.hasValidCnh(today);
        if (driver.isApproved() && !expiredCnh) {
            throw new DriverProfileLockedException();
        }

        String cpf = Cpf.digits(req.cpf());
        String plate = VehiclePlate.normalize(req.vehiclePlate());
        validateProfile(driver.getVehicleType(), req, today);
        String cpfHash = crypto.fingerprint(cpf);
        String cnhHash = crypto.fingerprint(req.cnhNumber());
        if (driverRepository.existsByCpfHashAndIdNot(cpfHash, driverId)
                || driverRepository.existsByCnhNumberHashAndIdNot(cnhHash, driverId)) {
            throw new DriverIdentityInUseException();
        }

        boolean cnhChanged = !Objects.equals(driver.getCnhNumber(), req.cnhNumber())
                || !Objects.equals(driver.getCnhCategory(), req.cnhCategory())
                || !Objects.equals(driver.getCnhExpiresOn(), req.cnhExpiresOn());
        boolean vehicleChanged = !Objects.equals(driver.getVehiclePlate(), plate)
                || !Objects.equals(driver.getVehicleBrand(), req.vehicleBrand().trim())
                || !Objects.equals(driver.getVehicleModel(), req.vehicleModel().trim())
                || !Objects.equals(driver.getVehicleYear(), req.vehicleYear().shortValue());

        driver.setCpf(cpf);
        driver.setCpfHash(cpfHash);
        driver.setCnhNumber(req.cnhNumber());
        driver.setCnhNumberHash(cnhHash);
        driver.setCnhCategory(req.cnhCategory());
        driver.setCnhExpiresOn(req.cnhExpiresOn());
        driver.setVehicleBrand(req.vehicleBrand().trim());
        driver.setVehicleModel(req.vehicleModel().trim());
        driver.setVehicleYear(req.vehicleYear().shortValue());
        driver.setVehicleColor(req.vehicleColor().trim());
        driver.setVehiclePlate(plate);

        // Documento ja analisado deixa de valer se o dado que ele comprova mudou.
        if (cnhChanged) {
            invalidateCurrent(driverId, DriverDocumentType.CNH, "Dados da CNH alterados: envie a CNH atualizada.");
        }
        if (vehicleChanged) {
            invalidateCurrent(driverId, DriverDocumentType.CRLV, "Dados do veículo alterados: envie o CRLV atualizado.");
        }
        if (driver.isApproved()) {
            // So chega aqui com a CNH vencida: a atualizacao volta para analise.
            driver.setApproved(false);
            driver.setOnline(false);
            log.info("Driver {} atualizou CNH vencida — volta para analise", driverId);
        }

        log.info("Driver {} atualizou o cadastro documental", driverId);
        return toResponse(driver, documentRepository.findByDriverIdOrderByCreatedAtDesc(driverId));
    }

    /**
     * Sem @Transactional de proposito: o upload ao storage (rede, ate alguns
     * MB) nao pode segurar conexao do pool. Checagem e gravacao rodam cada uma
     * na transacao curta do repository.
     */
    public DriverDocumentItem upload(UUID driverId, DriverDocumentType type, MultipartFile file) {
        Driver driver = loadDriver(driverId);
        DriverDocument current = documentRepository
                .findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, type).orElse(null);
        // CNH vencida libera reenvio da aprovada — mas nao duplica um envio ja em analise.
        boolean renewingExpiredCnh = type == DriverDocumentType.CNH
                && driver.getCnhExpiresOn() != null && !driver.hasValidCnh(today())
                && current != null && current.getStatus() == DriverDocumentStatus.APPROVED;
        if (current != null && current.getStatus() != DriverDocumentStatus.REJECTED && !renewingExpiredCnh) {
            throw new DocumentUploadNotAllowedException(current.getStatus() == DriverDocumentStatus.SUBMITTED
                    ? ALREADY_SUBMITTED
                    : "Este documento já está aprovado. Só é possível reenviar documento reprovado.");
        }

        byte[] content = readValidated(file);
        DocumentFileInspector.Detected detected = DocumentFileInspector.detect(content, type.acceptsPdf());
        if (detected == null) {
            throw new InvalidDocumentFileException(type.acceptsPdf()
                    ? "Formato não suportado. Envie JPG, PNG ou PDF."
                    : "Formato não suportado. Envie uma foto JPG ou PNG.");
        }

        content = DocumentFileInspector.sanitize(content, detected);

        UUID documentId = UUID.randomUUID();
        String path = driverId + "/" + type.name().toLowerCase() + "/" + documentId + "." + detected.extension();
        storage.put(path, content, detected.contentType());

        DriverDocument doc = new DriverDocument();
        doc.setId(documentId);
        doc.setDriverId(driverId);
        doc.setType(type);
        doc.setStatus(DriverDocumentStatus.SUBMITTED);
        doc.setStoragePath(path);
        doc.setContentType(detected.contentType());
        doc.setSizeBytes(content.length);
        try {
            DriverDocument saved = documentRepository.save(doc);
            log.info("Driver {} enviou {} ({} bytes, {})", driverId, type, content.length, detected.contentType());
            return DriverDocumentItem.from(saved);
        } catch (RuntimeException e) {
            // Sem linha no banco o arquivo vira orfao: remove (best-effort).
            try {
                storage.delete(List.of(path));
            } catch (RuntimeException cleanup) {
                log.warn("Arquivo orfao no storage apos falha ao gravar documento: {}", path);
            }
            // Clique duplo: o outro envio passou pelo mesmo check e gravou antes.
            if (e instanceof DataIntegrityViolationException dive && String.valueOf(
                    dive.getMostSpecificCause().getMessage()).contains("ux_driver_documents_one_submitted")) {
                log.warn("Envio duplicado de {} barrado pelo banco (driver {})", type, driverId);
                throw new DocumentUploadNotAllowedException(ALREADY_SUBMITTED);
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------------

    @Transactional
    public DriverDocumentItem approve(UUID driverId, UUID documentId, UUID adminId) {
        DriverDocument doc = loadCurrentDocument(driverId, documentId);
        if (doc.getStatus() != DriverDocumentStatus.SUBMITTED) {
            throw new DocumentReviewNotAllowedException("Só é possível aprovar documento aguardando análise.");
        }
        doc.setStatus(DriverDocumentStatus.APPROVED);
        doc.setRejectionReason(null);
        doc.setReviewedByAdminId(adminId);
        doc.setReviewedAt(Instant.now(clock));
        auditLog.record(adminId, AuditAction.DRIVER_DOCUMENT_APPROVED, "DRIVER_DOCUMENT", documentId,
                "driver=" + driverId + " type=" + doc.getType());
        log.info("Documento {} ({}) do driver {} aprovado pelo admin {}", documentId, doc.getType(), driverId, adminId);
        return DriverDocumentItem.from(doc);
    }

    @Transactional
    public DriverDocumentItem reject(UUID driverId, UUID documentId, UUID adminId, String reason) {
        DriverDocument doc = loadCurrentDocument(driverId, documentId);
        if (doc.getStatus() == DriverDocumentStatus.REJECTED) {
            throw new DocumentReviewNotAllowedException("Documento já está reprovado.");
        }
        doc.setStatus(DriverDocumentStatus.REJECTED);
        doc.setRejectionReason(reason.trim());
        doc.setReviewedByAdminId(adminId);
        doc.setReviewedAt(Instant.now(clock));

        Driver driver = loadDriver(driverId);
        if (driver.isApproved()) {
            driver.setApproved(false);
            driver.setOnline(false);
            log.warn("Driver {} perdeu a aprovacao: {} reprovado pelo admin {}", driverId, doc.getType(), adminId);
        }
        auditLog.record(adminId, AuditAction.DRIVER_DOCUMENT_REJECTED, "DRIVER_DOCUMENT", documentId,
                "driver=" + driverId + " type=" + doc.getType() + " motivo=" + reason.trim());
        return DriverDocumentItem.from(doc);
    }

    /**
     * URL assinada curta para o admin ver o arquivo. Toda emissao e auditada
     * (acesso a dado sensivel). Vale para qualquer envio do historico.
     */
    @Transactional
    public DocumentFileUrlResponse fileUrl(UUID driverId, UUID documentId, UUID adminId) {
        DriverDocument doc = documentRepository.findByIdAndDriverId(documentId, driverId)
                .orElseThrow(DriverDocumentNotFoundException::new);
        if (doc.getStoragePath() == null) {
            throw new DriverDocumentNotFoundException();
        }
        auditLog.record(adminId, AuditAction.DRIVER_DOCUMENT_VIEWED, "DRIVER_DOCUMENT", documentId,
                "driver=" + driverId + " type=" + doc.getType());
        DocumentStorage.SignedUrl signed = storage.signedUrl(doc.getStoragePath(),
                Duration.ofSeconds(storageProps.signedUrlTtlSeconds()));
        return new DocumentFileUrlResponse(signed.url(), signed.expiresAt());
    }

    // ------------------------------------------------------------------------
    // LGPD
    // ------------------------------------------------------------------------

    /**
     * Apaga do storage todos os arquivos do motorista e zera os caminhos (o
     * registro da analise fica, sem o arquivo). Chamado na exclusao de conta.
     */
    @Transactional
    public int purgeFiles(UUID driverId) {
        List<String> paths = documentRepository.findStoragePaths(driverId);
        if (paths.isEmpty()) {
            return 0;
        }
        storage.delete(paths);
        documentRepository.findByDriverIdOrderByCreatedAtDesc(driverId)
                .forEach(d -> d.setStoragePath(null));
        log.info("Arquivos de documento do driver {} expurgados ({})", driverId, paths.size());
        return paths.size();
    }

    /**
     * Retencao: apaga do storage, em lotes, os arquivos obsoletos (reprovados ou
     * substituidos ha mais que o prazo) e zera o caminho. O registro fica.
     */
    @Transactional
    public int purgeObsoleteFiles(Instant cutoff) {
        List<DriverDocument> obsolete = documentRepository.findObsoleteWithFile(cutoff, PageRequest.of(0, 500));
        if (obsolete.isEmpty()) {
            return 0;
        }
        storage.delete(obsolete.stream().map(DriverDocument::getStoragePath).toList());
        obsolete.forEach(d -> d.setStoragePath(null));
        return obsolete.size();
    }

    // ------------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------------

    private Driver loadDriver(UUID driverId) {
        return driverRepository.findById(driverId).orElseThrow(DriverNotFoundException::new);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BRAZIL));
    }

    private DriverDocument loadCurrentDocument(UUID driverId, UUID documentId) {
        DriverDocument doc = documentRepository.findByIdAndDriverId(documentId, driverId)
                .orElseThrow(DriverDocumentNotFoundException::new);
        DriverDocument current = documentRepository
                .findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, doc.getType()).orElseThrow();
        if (!current.getId().equals(doc.getId())) {
            throw new DocumentReviewNotAllowedException("Há um envio mais recente deste documento; analise o vigente.");
        }
        return doc;
    }

    private void invalidateCurrent(UUID driverId, DriverDocumentType type, String reason) {
        documentRepository.findFirstByDriverIdAndTypeOrderByCreatedAtDesc(driverId, type)
                .filter(d -> d.getStatus() != DriverDocumentStatus.REJECTED)
                .ifPresent(d -> {
                    d.setStatus(DriverDocumentStatus.REJECTED);
                    d.setRejectionReason(reason);
                    d.setReviewedByAdminId(null);
                    d.setReviewedAt(Instant.now(clock));
                });
    }

    private byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentFileException("Arquivo vazio.");
        }
        if (file.getSize() > storageProps.maxFileBytes()) {
            throw new InvalidDocumentFileException(
                    "Arquivo maior que " + (storageProps.maxFileBytes() / (1024 * 1024)) + " MB.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidDocumentFileException("Não foi possível ler o arquivo enviado.");
        }
    }

    private static void validateProfile(VehicleType vehicleType, DriverProfileRequest req, LocalDate today) {
        if (req.cnhExpiresOn().isBefore(today)) {
            throw new InvalidDriverProfileException("CNH vencida. Renove a CNH antes de se cadastrar.");
        }
        boolean categoryFits = vehicleType == VehicleType.MOTO
                ? req.cnhCategory().contains("A")
                : req.cnhCategory().chars().anyMatch(c -> c == 'B' || c == 'C' || c == 'D' || c == 'E');
        if (!categoryFits) {
            throw new InvalidDriverProfileException(vehicleType == VehicleType.MOTO
                    ? "Para moto a CNH precisa ter categoria A."
                    : "Para carro a CNH precisa ter categoria B (ou superior).");
        }
        if (req.vehicleYear() > today.getYear() + 1) {
            throw new InvalidDriverProfileException("Ano do veículo inválido.");
        }
    }

    private static Map<DriverDocumentType, DriverDocument> currentByType(List<DriverDocument> historyNewestFirst) {
        Map<DriverDocumentType, DriverDocument> current = new EnumMap<>(DriverDocumentType.class);
        historyNewestFirst.forEach(d -> current.putIfAbsent(d.getType(), d));
        return current;
    }

    private DocumentationStatus evaluate(Driver driver, Map<DriverDocumentType, DriverDocument> current,
                                         List<String> pending) {
        boolean incomplete = false;
        boolean rejected = false;
        boolean underReview = false;
        if (!driver.hasCompleteProfile()) {
            incomplete = true;
            pending.add("Preencher CPF, CNH e dados do veículo");
        }
        for (DriverDocumentType type : DriverDocumentType.values()) {
            DriverDocument doc = current.get(type);
            if (doc == null) {
                incomplete = true;
                pending.add("Enviar " + type);
            } else if (doc.getStatus() == DriverDocumentStatus.REJECTED) {
                rejected = true;
                pending.add("Reenviar " + type + ": " + doc.getRejectionReason());
            } else if (doc.getStatus() == DriverDocumentStatus.SUBMITTED) {
                underReview = true;
                pending.add(type + " aguardando análise");
            }
        }
        if (incomplete) {
            return DocumentationStatus.INCOMPLETE;
        }
        if (rejected) {
            return DocumentationStatus.REJECTED;
        }
        if (underReview) {
            return DocumentationStatus.PENDING_REVIEW;
        }
        if (!driver.hasValidCnh(today())) {
            pending.add("CNH vencida: atualize a validade e envie a CNH renovada");
            return DocumentationStatus.EXPIRED;
        }
        return DocumentationStatus.APPROVED;
    }

    private DriverDocumentationResponse toResponse(Driver driver, List<DriverDocument> history) {
        Map<DriverDocumentType, DriverDocument> current = currentByType(history);
        List<String> pending = new ArrayList<>();
        DocumentationStatus status = evaluate(driver, current, pending);
        return new DriverDocumentationResponse(
                driver.getUserId(),
                status,
                driver.isApproved(),
                DriverDocumentationResponse.Profile.from(driver),
                current.values().stream().map(DriverDocumentItem::from).toList(),
                history.stream().map(DriverDocumentItem::from).toList(),
                pending);
    }
}
