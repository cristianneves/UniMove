package com.unimove.domain.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phone_verifications")
@Getter
@Setter
@NoArgsConstructor
public class PhoneVerification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Codigo que viaja no link wa.me e volta dentro da mensagem do usuario. */
    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PhoneVerificationStatus status = PhoneVerificationStatus.PENDING;

    /** Telefone do remetente, como a Meta entregou. Nunca vem do formulario. */
    @Column(name = "verified_phone", length = 20)
    private String verifiedPhone;

    @Column(name = "token", length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 40)
    private RejectionReason rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Prazo para o usuario enviar a mensagem no WhatsApp. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Prazo para trocar o token por uma conta. So existe apos VERIFIED. */
    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = PhoneVerificationStatus.PENDING;
        }
    }
}
