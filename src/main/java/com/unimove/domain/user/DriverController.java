package com.unimove.domain.user;

import com.unimove.domain.user.dto.DriverStatusResponse;
import com.unimove.shared.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drivers/me")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping("/online")
    @PreAuthorize("hasRole('MOTORISTA')")
    public DriverStatusResponse online(@AuthenticationPrincipal AuthenticatedUser user) {
        return driverService.goOnline(user.userId());
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('MOTORISTA')")
    public DriverStatusResponse offline(@AuthenticationPrincipal AuthenticatedUser user) {
        return driverService.goOffline(user.userId());
    }
}
