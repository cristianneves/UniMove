package com.unimove.domain.audit;

import com.unimove.domain.audit.dto.AuditEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Auditoria", description = "Trilha das ações administrativas")
public class AdminAuditController {

    private final AdminAuditService service;

    public AdminAuditController(AdminAuditService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista a trilha de auditoria (mais recente primeiro)",
            description = "Filtros opcionais: `action` e `targetId` (ex.: id do usuário ou slug da cidade).")
    public Page<AuditEntryResponse> list(@RequestParam(required = false) AuditAction action,
                                         @RequestParam(required = false) String targetId,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return service.search(action, targetId, pageable);
    }
}
