package com.unimove.domain.user;

import com.unimove.domain.user.dto.DriverStatusResponse;
import com.unimove.domain.user.dto.PendingDriverItem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    public List<PendingDriverItem> pending() {
        return driverService.listPending();
    }

    @PostMapping("/{id}/approve")
    public DriverStatusResponse approve(@PathVariable UUID id) {
        return driverService.approve(id);
    }
}
