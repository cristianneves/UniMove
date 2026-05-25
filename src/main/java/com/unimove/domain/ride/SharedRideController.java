package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.SharedRideResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/share")
public class SharedRideController {

    private final RideShareService rideShareService;

    public SharedRideController(RideShareService rideShareService) {
        this.rideShareService = rideShareService;
    }

    @GetMapping("/{token}")
    public SharedRideResponse get(@PathVariable UUID token) {
        return rideShareService.getByToken(token);
    }
}
