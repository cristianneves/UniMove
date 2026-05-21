package com.unimove.domain.user;

import com.unimove.domain.user.dto.CreateSavedPlaceRequest;
import com.unimove.domain.user.dto.SavedPlaceResponse;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/saved-places")
@PreAuthorize("hasRole('PASSAGEIRO')")
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    public SavedPlaceController(SavedPlaceService savedPlaceService) {
        this.savedPlaceService = savedPlaceService;
    }

    @PostMapping
    public ResponseEntity<SavedPlaceResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @Valid @RequestBody CreateSavedPlaceRequest req) {
        SavedPlaceResponse created = savedPlaceService.create(user.userId(), req);
        return ResponseEntity.created(URI.create("/saved-places/" + created.id())).body(created);
    }

    @GetMapping
    public List<SavedPlaceResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return savedPlaceService.list(user.userId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID id) {
        savedPlaceService.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
