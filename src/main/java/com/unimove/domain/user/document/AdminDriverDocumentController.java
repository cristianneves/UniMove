package com.unimove.domain.user.document;

import com.unimove.domain.user.document.dto.DocumentFileUrlResponse;
import com.unimove.domain.user.document.dto.DriverDocumentItem;
import com.unimove.domain.user.document.dto.DriverDocumentationResponse;
import com.unimove.domain.user.document.dto.RejectDocumentRequest;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/drivers/{driverId}")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Documentação de motorista", description = "Revisão dos documentos (RF02)")
public class AdminDriverDocumentController {

    private final DriverDocumentService service;

    public AdminDriverDocumentController(DriverDocumentService service) {
        this.service = service;
    }

    @GetMapping("/documentation")
    @Operation(summary = "Dados declarados, documentos vigentes e histórico de envios do motorista",
            description = "Mostra CPF e CNH: o acesso fica na trilha de auditoria.")
    public DriverDocumentationResponse get(@AuthenticationPrincipal AuthenticatedUser admin,
                                           @PathVariable UUID driverId) {
        return service.getDocumentationForAdmin(driverId, admin.userId());
    }

    @GetMapping("/documents/{documentId}/file")
    @Operation(summary = "URL assinada (curta) para visualizar o arquivo",
            description = "Cada emissão fica na trilha de auditoria (acesso a dado sensível).")
    public DocumentFileUrlResponse file(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @PathVariable UUID driverId,
                                        @PathVariable UUID documentId) {
        return service.fileUrl(driverId, documentId, admin.userId());
    }

    @PostMapping("/documents/{documentId}/approve")
    @Operation(summary = "Aprova o envio vigente de um documento")
    public DriverDocumentItem approve(@AuthenticationPrincipal AuthenticatedUser admin,
                                      @PathVariable UUID driverId,
                                      @PathVariable UUID documentId) {
        return service.approve(driverId, documentId, admin.userId());
    }

    @PostMapping("/documents/{documentId}/reject")
    @Operation(summary = "Reprova com motivo (visível ao motorista)",
            description = "Se o motorista estava aprovado, perde a aprovação e sai de operação até reenviar.")
    public DriverDocumentItem reject(@AuthenticationPrincipal AuthenticatedUser admin,
                                     @PathVariable UUID driverId,
                                     @PathVariable UUID documentId,
                                     @Valid @RequestBody RejectDocumentRequest req) {
        return service.reject(driverId, documentId, admin.userId(), req.reason());
    }
}
