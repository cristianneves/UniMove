package com.unimove.domain.notification;

import com.unimove.domain.notification.dto.NotificationResponse;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
@Tag(name = "Notificações", description = "Caixa de entrada in-app (sem push no MVP)")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Caixa de entrada do usuário, mais recentes primeiro")
    public Page<NotificationResponse> inbox(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return service.inbox(user.userId(), pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Quantidade de não lidas (badge do app)")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("unread", service.unreadCount(user.userId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca como lida (idempotente)")
    public void markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.markRead(user.userId(), id);
    }
}
