package com.unimove.domain.user;

import com.unimove.domain.user.dto.PendingDriverItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    @Query("""
            SELECT new com.unimove.domain.user.dto.PendingDriverItem(
                d.user.id, d.user.name, d.user.email, d.user.phone, d.user.cidade,
                d.vehicleType, d.vehiclePlate, d.user.createdAt
            )
            FROM Driver d
            WHERE d.approved = false
            ORDER BY d.user.createdAt ASC
            """)
    List<PendingDriverItem> findPending();

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
