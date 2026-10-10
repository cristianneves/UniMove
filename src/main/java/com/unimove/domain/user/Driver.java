package com.unimove.domain.user;

import com.unimove.shared.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
public class Driver {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    public UUID getUserId() {
        return id;
    }

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    @Column(name = "vehicle_plate", nullable = false, length = 10)
    private String vehiclePlate;

    // --- Cadastro documental (V27). Nulos ate o motorista preencher. ---

    /** Cifrado no banco (AES-GCM, V31); a unicidade vai por {@link #cpfHash}. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cpf", length = 255)
    private String cpf;

    @Column(name = "cpf_hash", length = 64)
    private String cpfHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cnh_number", length = 255)
    private String cnhNumber;

    @Column(name = "cnh_number_hash", length = 64)
    private String cnhNumberHash;

    @Column(name = "cnh_category", length = 2)
    private String cnhCategory;

    @Column(name = "cnh_expires_on")
    private LocalDate cnhExpiresOn;

    @Column(name = "vehicle_brand", length = 40)
    private String vehicleBrand;

    @Column(name = "vehicle_model", length = 60)
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Short vehicleYear;

    @Column(name = "vehicle_color", length = 30)
    private String vehicleColor;

    /** Todos os dados declarados do RF02 preenchidos. */
    public boolean hasCompleteProfile() {
        return cpf != null && cnhNumber != null && cnhCategory != null && cnhExpiresOn != null
                && vehicleBrand != null && vehicleModel != null && vehicleYear != null && vehicleColor != null;
    }

    /** CNH cadastrada e valida no dia informado (vence no fim do dia de validade). */
    public boolean hasValidCnh(LocalDate today) {
        return cnhExpiresOn != null && !cnhExpiresOn.isBefore(today);
    }
}
