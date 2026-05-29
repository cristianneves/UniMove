package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.AcceptRideRequest;
import com.unimove.domain.ride.dto.CancelRideRequest;
import com.unimove.domain.ride.dto.ConfirmPaymentRequest;
import com.unimove.domain.ride.dto.CreateRideRequest;
import com.unimove.domain.ride.dto.EstimateRequest;
import com.unimove.domain.ride.dto.EstimateResponse;
import com.unimove.domain.ride.dto.RatingResponse;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.ride.dto.RideMuralItem;
import com.unimove.domain.ride.dto.RideResponse;
import com.unimove.domain.ride.dto.RideRouteResponse;
import com.unimove.domain.ride.dto.SubmitRatingRequest;
import com.unimove.domain.ride.dto.UpdateDriverLocationRequest;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @PostMapping("/estimate")
    @PreAuthorize("hasRole('PASSAGEIRO')")
    public EstimateResponse estimate(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody EstimateRequest req) {
        return rideService.estimate(user, req);
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

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
    public Page<RideHistoryItem> history(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(required = false) RideStatus status,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return rideService.history(user, status, pageable);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RideResponse accept(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID id,
                               @Valid @RequestBody(required = false) AcceptRideRequest req) {
        return rideService.accept(user, id, req);
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

    @PutMapping("/{id}/driver-location")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RideResponse updateDriverLocation(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody UpdateDriverLocationRequest req) {
        return rideService.updateDriverLocation(user, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
    public RideResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                            @PathVariable UUID id) {
        return rideService.get(user, id);
    }

    @GetMapping("/{id}/route")
    @PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
    public RideRouteResponse route(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID id) {
        return rideService.getRoute(user, id);
    }

    @GetMapping(value = "/{id}/status-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
    public SseEmitter statusStream(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID id) {
        return rideService.subscribeStatus(user, id);
    }

    @PostMapping("/{id}/rating")
    @PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA')")
    public RatingResponse submitRating(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody SubmitRatingRequest req) {
        return rideService.submitRating(user, id, req);
    }
}
