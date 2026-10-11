package com.unimove.domain.user;

import com.unimove.domain.user.dto.AuthResponse;
import com.unimove.domain.user.dto.LoginRequest;
import com.unimove.domain.user.dto.RegisterRequest;
import com.unimove.domain.user.dto.ResetPasswordRequest;
import com.unimove.domain.user.dto.SocialAuthResponse;
import com.unimove.domain.user.dto.SocialLoginRequest;
import com.unimove.domain.user.dto.SocialRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final UserProfileService userProfileService;

    public AuthController(AuthService authService, SocialAuthService socialAuthService,
                          UserProfileService userProfileService) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.userProfileService = userProfileService;
    }

    @PostMapping("/register")
    @Operation(summary = "Cadastra o usuário e emite o JWT",
            description = "`role` aceita apenas `PASSAGEIRO` ou `MOTORISTA` — `ADMIN` é rejeitado com 400. "
                    + "Contas ADMIN são criadas exclusivamente por seed/migration.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse body = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e emite o JWT",
            description = "Após 5 falhas consecutivas para o mesmo e-mail, o login é bloqueado "
                    + "por 15 minutos e retorna 429 (configurável via `app.auth.lockout`).")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/social")
    @Operation(summary = "Entra com um provedor social (Google)",
            description = "Recebe o `idToken` do sign-in nativo do app. Responde 200 com "
                    + "`status=AUTHENTICATED` (JWT em `auth`) ou `status=REGISTRATION_REQUIRED` "
                    + "(primeiro acesso — o app segue para o desafio do WhatsApp e depois chama "
                    + "`/auth/social/register`). Conta existente com o mesmo e-mail é vinculada "
                    + "automaticamente, desde que o provedor confirme o e-mail. "
                    + "Retorna 503 se o login social não estiver configurado no ambiente.")
    public ResponseEntity<SocialAuthResponse> social(@Valid @RequestBody SocialLoginRequest req) {
        return ResponseEntity.ok(socialAuthService.login(req));
    }

    @PostMapping("/social/register")
    @Operation(summary = "Completa o primeiro acesso via provedor social",
            description = "Reenvia o mesmo `idToken` (revalidado aqui) junto do `verificationToken` "
                    + "do desafio do WhatsApp, `role` e `cidade`. A conta nasce sem senha. "
                    + "O provedor social substitui a senha, nunca a verificação do telefone.")
    public ResponseEntity<AuthResponse> socialRegister(@Valid @RequestBody SocialRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(socialAuthService.register(req));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Esqueci minha senha (sem sessão)",
            description = "Recebe o `verificationToken` de um desafio `PASSWORD_RESET` verificado no WhatsApp "
                    + "(`POST /auth/phone/challenge` com `{\"purpose\":\"PASSWORD_RESET\"}`) e a senha nova. "
                    + "Revoga as sessões anteriores e responde 204: o app segue para o login. "
                    + "Token ausente, vencido, já usado ou de cadastro → 400.")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        userProfileService.resetPasswordWithPhone(req.verificationToken(), req.newPassword());
        return ResponseEntity.noContent().build();
    }
}
