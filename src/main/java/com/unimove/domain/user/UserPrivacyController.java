package com.unimove.domain.user;

import com.unimove.domain.user.dto.AcceptTermsRequest;
import com.unimove.domain.user.dto.DataExportResponse;
import com.unimove.domain.user.dto.DeleteAccountRequest;
import com.unimove.domain.user.dto.PrivacyPreferencesResponse;
import com.unimove.domain.user.dto.UpdatePreferencesRequest;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
@Tag(name = "Privacidade", description = "Direitos do titular (LGPD), consentimento e preferências")
public class UserPrivacyController {

    private final AccountPrivacyService service;

    public UserPrivacyController(AccountPrivacyService service) {
        this.service = service;
    }

    @GetMapping("/preferences")
    @Operation(summary = "Preferências de comunicação e situação do consentimento")
    public PrivacyPreferencesResponse preferences(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.getPreferences(user);
    }

    @PutMapping("/preferences")
    @Operation(summary = "Comunicação promocional e preferências do pedido",
            description = "Notificações transacionais (corrida, segurança, conta) não são opcionais. "
                    + "Passageiro pode guardar pagamento e categoria preferidos (PUT substitui: ausente = sem "
                    + "preferência); motorista que os envia recebe 400.")
    public PrivacyPreferencesResponse updatePreferences(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @Valid @RequestBody UpdatePreferencesRequest req) {
        return service.updatePreferences(user, req);
    }

    @PostMapping("/terms")
    @Operation(summary = "Aceita a versão vigente dos termos (reaceite após atualização)")
    public PrivacyPreferencesResponse acceptTerms(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody AcceptTermsRequest req) {
        return service.acceptTerms(user, req.version());
    }

    @GetMapping("/data-export")
    @Operation(summary = "Exporta os dados pessoais do usuário (JSON)",
            description = "Perfil, preferências, locais salvos, corridas, mensagens enviadas e, para motorista, "
                    + "os dados da documentação (sem os arquivos).")
    public DataExportResponse export(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.export(user);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a conta (anonimização imediata)",
            description = "Exige a senha atual quando a conta tem senha. 409 com corrida em andamento. "
                    + "Corridas ficam retidas sem dados identificáveis; o token atual deixa de valer.")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,
                       @Valid @RequestBody(required = false) DeleteAccountRequest req) {
        service.deleteAccount(user, req == null ? null : req.password());
    }
}
