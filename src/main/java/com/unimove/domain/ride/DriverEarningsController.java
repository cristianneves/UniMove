package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.EarningsResponse;
import com.unimove.shared.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/drivers/me/earnings")
@PreAuthorize("hasRole('MOTORISTA')")
public class DriverEarningsController {

    private final RideService rideService;

    public DriverEarningsController(RideService rideService) {
        this.rideService = rideService;
    }

    @GetMapping
    public EarningsResponse earnings(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return rideService.getDriverEarnings(user.userId(), from, to);
    }
}
