package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSAGEIRO')")
    public ResponseEntity<RideResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody CreateRideRequest req) {
        RideResponse body = rideService.create(user, req);
        return ResponseEntity.created(URI.create("/rides/" + body.id())).body(body);
    }
}
