package com.unimove.shared.util;

/**
 * Placa no padrao antigo (ABC1234) ou Mercosul (ABC1D23). Guardada sempre
 * sem hifen e em maiusculas: assim "ABC-1234" e "abc1234" sao a mesma placa
 * e trocar so a grafia nao conta como veiculo alterado.
 */
public final class VehiclePlate {

    /** Aceita o hifen opcional da grafia antiga; usado nos @Pattern dos DTOs. */
    public static final String PATTERN = "\\s*[A-Za-z]{3}-?\\d[A-Za-z0-9]\\d{2}\\s*";
    public static final String MESSAGE = "Placa inválida (ABC1234 ou ABC1D23).";

    private VehiclePlate() {}

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().replace("-", "").toUpperCase();
    }
}
