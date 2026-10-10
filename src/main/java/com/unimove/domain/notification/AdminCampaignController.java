package com.unimove.domain.notification;

import com.unimove.domain.notification.dto.CampaignResponse;
import com.unimove.domain.notification.dto.CreateCampaignRequest;
import com.unimove.domain.user.Role;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/campaigns")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Campanhas", description = "Comunicação promocional in-app (só para quem deu opt-in)")
public class AdminCampaignController {

    private final NotificationService service;

    public AdminCampaignController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/audience")
    @Operation(summary = "Quantos receberiam a campanha (ativos com opt-in) no segmento")
    public Map<String, Long> audience(@RequestParam(required = false) String cidade,
                                      @RequestParam(required = false) Role role) {
        return Map.of("recipients", service.previewAudience(cidade, role));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia a campanha para a caixa de entrada do segmento",
            description = "Só alcança contas ativas com opt-in promocional. Sem público, fica registrada "
                    + "como NO_AUDIENCE. O envio fica na trilha de auditoria.")
    public CampaignResponse send(@AuthenticationPrincipal AuthenticatedUser admin,
                                 @Valid @RequestBody CreateCampaignRequest req) {
        return service.send(admin.userId(), req);
    }

    @GetMapping
    @Operation(summary = "Histórico de campanhas (mais recentes primeiro)")
    public Page<CampaignResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.listCampaigns(pageable);
    }
}
