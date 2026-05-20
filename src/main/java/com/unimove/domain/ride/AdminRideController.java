package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AdminRideItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rides")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRideController {

    private final RideService rideService;

    public AdminRideController(RideService rideService) {
        this.rideService = rideService;
    }

    @GetMapping
    public Page<AdminRideItem> list(@PageableDefault(size = 20, sort = "createdAt",
            direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return rideService.listAdminRides(pageable);
    }
}
