package com.unimove.domain.verification;

import com.unimove.domain.verification.dto.ChallengeResponse;
import com.unimove.domain.verification.dto.ChallengeStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints publicos do desafio de telefone. Ficam sob /auth para herdar o
 * permitAll de {@code SecurityConfig} — sao necessariamente pre-autenticacao,
 * ja que a conta ainda nao existe.
 */
@RestController
@RequestMapping("/auth/phone")
public class PhoneVerificationController {

    private final PhoneVerificationService service;

    public PhoneVerificationController(PhoneVerificationService service) {
        this.service = service;
    }

    @PostMapping("/challenge")
    @Operation(summary = "Cria um desafio de verificação de telefone",
            description = "Retorna um link `wa.me` com o código pré-preenchido. O backend **não envia** "
                    + "mensagem alguma: quem envia é o usuário, e é isso que mantém o custo em zero "
                    + "na Cloud API. Limitado por IP (429 ao estourar).")
    public ResponseEntity<ChallengeResponse> createChallenge(HttpServletRequest request) {
        ChallengeResponse body = service.createChallenge(clientIp(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/challenge/{challengeId}")
    @Operation(summary = "Consulta o status do desafio (polling)",
            description = "`VERIFIED` traz o `verificationToken` a ser enviado em `POST /auth/register`. "
                    + "`REJECTED` com `PHONE_IN_USE` significa que o número já tem conta — o caminho é login.")
    public ResponseEntity<ChallengeStatusResponse> getStatus(@PathVariable UUID challengeId) {
        return ResponseEntity.ok(service.getStatus(challengeId));
    }

    /**
     * Atras do proxy do Railway o IP real vem em X-Forwarded-For; o primeiro
     * item da lista e o cliente. Cai para o IP da conexao quando ausente.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
