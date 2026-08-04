package com.unimove.domain.user.social;

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

/**
 * Vinculo entre um usuario e a conta dele num provedor social.
 *
 * <p>Guarda {@code userId} solto em vez de {@code @ManyToOne User}: nao ha
 * necessidade de navegar a entidade e isso mantem o mapeamento trivial.
 */
@Entity
@Table(name = "social_identities")
@Getter
@Setter
@NoArgsConstructor
public class SocialIdentityEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    /** O {@code sub} do provedor — chave estavel, imune a troca de e-mail. */
    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    /** E-mail no momento do vinculo; informativo, nao e chave de busca. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
