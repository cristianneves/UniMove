package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.PricingConfigRequest;
import com.unimove.domain.ride.dto.PricingConfigResponse;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final PricingConfigService service;

    public AdminPricingController(PricingConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<PricingConfigResponse> list() {
        return service.list();
    }

    @PutMapping
    public PricingConfigResponse upsert(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @Valid @RequestBody PricingConfigRequest req) {
        return service.upsert(admin.userId(), req);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser admin,
                                       @RequestParam String cidade,
                                       @RequestParam RideCategory category) {
        service.delete(admin.userId(), cidade, category);
        return ResponseEntity.noContent().build();
    }
}
