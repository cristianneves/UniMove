package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminCancelRideRequest;
import com.unimove.domain.ride.dto.AdminRideItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/admin/rides")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRideController {

    private final RideService rideService;

    public AdminRideController(RideService rideService) {
        this.rideService = rideService;
    }

    @GetMapping
    public Page<AdminRideItem> list(@RequestParam(required = false) PaymentStatus paymentStatus,
                                    @RequestParam(required = false) RideStatus status,
                                    @RequestParam(required = false) String cidade,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @PageableDefault(size = 20, sort = "createdAt",
            direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return rideService.listAdminRides(paymentStatus, status, cidade, from, to, pageable);
    }

    @GetMapping("/{id}")
    public RideResponse get(@PathVariable UUID id) {
        return rideService.getForAdmin(id);
    }

    @PostMapping("/{id}/cancel")
    public RideResponse cancel(@AuthenticationPrincipal AuthenticatedUser admin,
                               @PathVariable UUID id,
                               @Valid @RequestBody AdminCancelRideRequest req) {
        return rideService.cancelByAdmin(id, admin.userId(), req.reason());
    }
}
