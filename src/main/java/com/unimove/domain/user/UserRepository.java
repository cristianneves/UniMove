package com.unimove.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    Optional<User> findByPhone(String phone);

    @Query("""
            select new com.unimove.domain.user.AccountSnapshot(u.status, u.tokensValidAfter, u.mustChangePassword)
            from User u where u.id = :id
            """)
    Optional<AccountSnapshot> findAccountSnapshot(@Param("id") UUID id);

    @Query("select u.status from User u where u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") UUID id);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    long countByRole(Role role);

    long countByRoleAndStatusNot(Role role, UserStatus status);

    /** Publico de campanha: so ativo, com opt-in promocional e nunca admin (LGPD). */
    @Query("""
            SELECT u.id FROM User u
            WHERE u.marketingOptIn = true
              AND u.status = com.unimove.domain.user.UserStatus.ACTIVE
              AND u.role <> com.unimove.domain.user.Role.ADMIN
              AND (:cidade IS NULL OR u.cidade = :cidade)
              AND (:role IS NULL OR u.role = :role)
            """)
    List<UUID> findMarketingAudience(@Param("cidade") String cidade, @Param("role") Role role);

    @Query("""
            SELECT count(u) FROM User u
            WHERE u.marketingOptIn = true
              AND u.status = com.unimove.domain.user.UserStatus.ACTIVE
              AND u.role <> com.unimove.domain.user.Role.ADMIN
              AND (:cidade IS NULL OR u.cidade = :cidade)
              AND (:role IS NULL OR u.role = :role)
            """)
    long countMarketingAudience(@Param("cidade") String cidade, @Param("role") Role role);

    /** Busca do admin: texto em nome/e-mail/telefone + filtros opcionais. Excluidos so se pedidos por status. */
    @Query(
            value = """
                    SELECT u FROM User u
                    WHERE (:q IS NULL OR LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR u.phone LIKE :q)
                      AND (:role IS NULL OR u.role = :role)
                      AND (:status IS NULL OR u.status = :status)
                      AND (:status IS NOT NULL OR u.status <> com.unimove.domain.user.UserStatus.DELETED)
                      AND (:cidade IS NULL OR u.cidade = :cidade)
                    """,
            countQuery = """
                    SELECT count(u) FROM User u
                    WHERE (:q IS NULL OR LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR u.phone LIKE :q)
                      AND (:role IS NULL OR u.role = :role)
                      AND (:status IS NULL OR u.status = :status)
                      AND (:status IS NOT NULL OR u.status <> com.unimove.domain.user.UserStatus.DELETED)
                      AND (:cidade IS NULL OR u.cidade = :cidade)
                    """
    )
    Page<User> search(@Param("q") String q, @Param("role") Role role, @Param("status") UserStatus status,
                      @Param("cidade") String cidade, Pageable pageable);

    List<User> findByRole(Role role);

    long countByStatus(UserStatus status);

    long countByCidade(String cidade);
}
