package com.unimove.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @Modifying
    @Query("UPDATE Driver d SET d.lastSeenAt = :now WHERE d.user.id = :userId")
    int updateLastSeenAt(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Marca offline, em lote, motoristas online sem atividade desde o cutoff.
     * Usado pelo {@code DriverAutoOfflineScheduler}. lastSeenAt nulo tambem
     * conta como inativo (motorista que nunca fez request apos ficar online).
     */
    @Modifying
    @Query("""
            UPDATE Driver d SET d.online = false
            WHERE d.online = true
              AND (d.lastSeenAt IS NULL OR d.lastSeenAt < :cutoff)
            """)
    int markStaleOnlineDriversOffline(@Param("cutoff") Instant cutoff);

    /** Motoristas aguardando aprovacao (sem os excluidos), mais antigos primeiro. */
    @Query(
            value = """
                    SELECT d FROM Driver d JOIN FETCH d.user u
                    WHERE d.approved = false AND u.status <> com.unimove.domain.user.UserStatus.DELETED
                    ORDER BY u.createdAt ASC
                    """,
            countQuery = """
                    SELECT count(d) FROM Driver d
                    WHERE d.approved = false AND d.user.status <> com.unimove.domain.user.UserStatus.DELETED
                    """
    )
    Page<Driver> findPending(Pageable pageable);

    @Query("SELECT count(d) FROM Driver d WHERE d.user.status <> com.unimove.domain.user.UserStatus.DELETED")
    long countNotDeleted();

    @Query("""
            SELECT count(d) FROM Driver d
            WHERE d.approved = false AND d.user.status <> com.unimove.domain.user.UserStatus.DELETED
            """)
    long countPendingNotDeleted();

    boolean existsByCpfHashAndIdNot(String cpfHash, UUID id);

    boolean existsByCnhNumberHashAndIdNot(String cnhNumberHash, UUID id);

    long countByOnlineTrue();

    long countByApprovedFalse();

    /** Motoristas online de uma cidade para um tipo de veiculo — denominador bruto do surge. */
    @Query("""
            SELECT COUNT(d) FROM Driver d
            WHERE d.online = true
              AND d.user.cidade = :cidade
              AND d.vehicleType = :vehicleType
            """)
    long countOnlineByCidadeAndVehicleType(@Param("cidade") String cidade,
                                           @Param("vehicleType") VehicleType vehicleType);
}
