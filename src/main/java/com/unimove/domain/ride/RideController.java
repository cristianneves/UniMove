package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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

    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasRole('PASSAGEIRO')")
    public RideResponse confirmPayment(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody ConfirmPaymentRequest req) {
        return rideService.confirmPayment(user, id, req);
    }

    @GetMapping("/mural")
    @PreAuthorize("hasRole('MOTORISTA')")
    public List<RideMuralItem> mural(@AuthenticationPrincipal AuthenticatedUser user) {
        return rideService.listMural(user);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RideResponse accept(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID id) {
        return rideService.accept(user, id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RideResponse start(@AuthenticationPrincipal AuthenticatedUser user,
                              @PathVariable UUID id) {
        return rideService.start(user, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RideResponse complete(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable UUID id) {
        return rideService.complete(user, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
    public RideResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID id,
                               @Valid @RequestBody(required = false) CancelRideRequest req) {
        return rideService.cancel(user, id, req);
    }
}
