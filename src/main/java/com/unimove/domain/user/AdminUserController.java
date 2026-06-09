package com.unimove.domain.user;

import com.unimove.domain.user.dto.AdminResetPasswordResponse;
import com.unimove.domain.user.dto.SuspendUserRequest;
import com.unimove.domain.user.dto.UserStatusResponse;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAccountService userAccountService;
    private final UserProfileService userProfileService;

    public AdminUserController(UserAccountService userAccountService,
                               UserProfileService userProfileService) {
        this.userAccountService = userAccountService;
        this.userProfileService = userProfileService;
    }

    @GetMapping("/suspended")
    public Page<UserStatusResponse> listSuspended(Pageable pageable) {
        return userAccountService.listSuspended(pageable);
    }

    @PostMapping("/{id}/suspend")
    public UserStatusResponse suspend(@AuthenticationPrincipal AuthenticatedUser admin,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody SuspendUserRequest req) {
        return userAccountService.suspend(id, admin.userId(), req.reason());
    }

    @PostMapping("/{id}/reactivate")
    public UserStatusResponse reactivate(@AuthenticationPrincipal AuthenticatedUser admin,
                                         @PathVariable UUID id) {
        return userAccountService.reactivate(id, admin.userId());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reseta a senha de um usuário (fluxo 'esqueci minha senha' do MVP)",
            description = "Gera uma senha temporária exibida uma única vez na resposta. "
                    + "O admin repassa ao usuário por canal externo. Não permitido para contas ADMIN.")
    public AdminResetPasswordResponse resetPassword(@AuthenticationPrincipal AuthenticatedUser admin,
                                                    @PathVariable UUID id) {
        return userProfileService.resetPassword(id, admin.userId());
    }
}
