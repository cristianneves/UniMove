package com.unimove.domain.user;

import com.unimove.domain.user.dto.SuspendUserRequest;
import com.unimove.domain.user.dto.UserStatusResponse;
import com.unimove.shared.security.AuthenticatedUser;
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

    public AdminUserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
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
}
