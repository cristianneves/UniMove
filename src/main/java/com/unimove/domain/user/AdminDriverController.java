package com.unimove.domain.user;

import com.unimove.domain.user.dto.DriverStatusResponse;
import com.unimove.domain.user.dto.PendingDriverItem;
import com.unimove.shared.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/drivers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDriverController {

    private final DriverService driverService;

    public AdminDriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/pending")
    public Page<PendingDriverItem> pending(@PageableDefault(size = 20) Pageable pageable) {
        return driverService.listPending(pageable);
    }

    @PostMapping("/{id}/approve")
    public DriverStatusResponse approve(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @PathVariable UUID id) {
        return driverService.approve(id, admin.userId());
    }
}
