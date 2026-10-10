package com.unimove.domain.user;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RideCategory;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Nulo em conta criada por login social — o Google substitui a senha (V20). */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    /** Preenchido no cadastro, com o instante em que a Meta confirmou a posse do numero. */
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "cidade", nullable = false, length = 80)
    private String cidade;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rating_avg", nullable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;

    @Column(name = "suspended_by_admin_id")
    private UUID suspendedByAdminId;

    /** Versao dos termos/politica de privacidade aceita e quando (V28). */
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /** Comunicacao promocional so com opt-in explicito. */
    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "marketing_opt_in_at")
    private Instant marketingOptInAt;

    /** Pre-selecao da tela de pedido do passageiro (V34). Nulo = sem preferencia. */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_payment_method", length = 10)
    private PaymentMethod preferredPaymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_category", length = 10)
    private RideCategory preferredCategory;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** JWT emitido antes disto nao vale mais (troca/reset de senha, V29). */
    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    /** Senha temporaria do reset: so serve para escolher outra (V29). */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (ratingAvg == null) {
            ratingAvg = BigDecimal.ZERO;
        }
        if (ratingCount == null) {
            ratingCount = 0;
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }
}
