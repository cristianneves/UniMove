package com.unimove.domain.user.document;

import com.unimove.domain.user.document.dto.PendingDocumentItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/drivers/documents")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Documentação de motorista", description = "Revisão dos documentos (RF02)")
public class AdminDocumentQueueController {

    private final DriverDocumentService service;

    public AdminDocumentQueueController(DriverDocumentService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    @Operation(summary = "Fila de documentos aguardando análise (mais antigos primeiro)")
    public Page<PendingDocumentItem> pending(@PageableDefault(size = 20) Pageable pageable) {
        return service.pendingDocuments(pageable);
    }
}
