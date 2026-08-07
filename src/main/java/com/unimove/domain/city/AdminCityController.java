package com.unimove.domain.city;

import com.unimove.domain.city.dto.AdminCityItem;
import com.unimove.domain.city.dto.OpenCityRequest;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/cities")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Cidades", description = "Abertura e fechamento de cidades atendidas")
public class AdminCityController {

    private final CityCatalogService service;

    public AdminCityController(CityCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista todas as cidades",
            description = "Inclui as desativadas. `userCount` diz quantas contas operam em cada uma.")
    public List<AdminCityItem> list() {
        return service.listAll();
    }

    @PostMapping
    @Operation(summary = "Abre (ou reabre) uma cidade",
            description = "O slug vem da normalização do nome informado. 201 quando a cidade é nova, "
                    + "200 quando uma cidade desativada é reativada.")
    public ResponseEntity<AdminCityItem> open(@AuthenticationPrincipal AuthenticatedUser admin,
                                              @Valid @RequestBody OpenCityRequest req) {
        CityCatalogService.OpenResult result = service.open(admin.userId(), req.cidade());
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.city());
    }

    @DeleteMapping("/{slug}")
    @Operation(summary = "Desativa uma cidade",
            description = "Soft delete: quem já está cadastrado ali continua operando normalmente; "
                    + "a cidade apenas deixa de ser oferecida em novos cadastros e trocas de perfil.")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal AuthenticatedUser admin,
                                           @PathVariable String slug) {
        service.deactivate(admin.userId(), slug);
        return ResponseEntity.noContent().build();
    }
}
