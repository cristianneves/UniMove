package com.unimove.domain.user;

import com.unimove.domain.user.dto.ChangePasswordRequest;
import com.unimove.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@PreAuthorize("hasAnyRole('PASSAGEIRO', 'MOTORISTA', 'ADMIN')")
@Tag(name = "Perfil", description = "Perfil do usuário autenticado")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Troca a senha do usuário autenticado",
            description = "Exige a senha atual. Retorna 400 se a senha atual estiver incorreta.")
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest req) {
        userProfileService.changePassword(user, req.currentPassword(), req.newPassword());
    }
}
