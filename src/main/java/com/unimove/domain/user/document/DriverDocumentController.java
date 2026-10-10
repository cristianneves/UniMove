package com.unimove.domain.user.document;

import com.unimove.domain.user.document.dto.DriverDocumentItem;
import com.unimove.domain.user.document.dto.DriverDocumentationResponse;
import com.unimove.domain.user.document.dto.DriverProfileRequest;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/drivers/me/documentation")
@PreAuthorize("hasRole('MOTORISTA')")
@Tag(name = "Motorista — Documentação", description = "Cadastro documental (CPF, CNH, veículo e arquivos)")
public class DriverDocumentController {

    private final DriverDocumentService service;

    public DriverDocumentController(DriverDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Situação da documentação",
            description = "`status` consolidado, dados declarados, envio vigente de cada documento, histórico "
                    + "e `pending` com o que falta (inclui o motivo de cada reprovação).")
    public DriverDocumentationResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.getDocumentation(user.userId());
    }

    @PutMapping("/profile")
    @Operation(summary = "Preenche CPF, CNH e veículo",
            description = "409 se o cadastro já está aprovado (exceto com CNH vencida). Mudar dados da CNH ou do "
                    + "veículo invalida o documento correspondente já enviado.")
    public DriverDocumentationResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @Valid @RequestBody DriverProfileRequest req) {
        return service.updateProfile(user.userId(), req);
    }

    @PostMapping(value = "/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia CNH, CRLV ou SELFIE (multipart, campo `file`)",
            description = "JPG/PNG (e PDF para CNH/CRLV), até 5 MB; o tipo é conferido pelo conteúdo. "
                    + "Só aceita o primeiro envio ou o reenvio de documento reprovado. 503 se o storage "
                    + "não estiver configurado.")
    public DriverDocumentItem upload(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable DriverDocumentType type,
                                     @RequestPart("file") MultipartFile file) {
        return service.upload(user.userId(), type, file);
    }
}
