package com.unimove.shared.util;

/**
 * Digitos verificadores do numero de registro da CNH (11 digitos), pelo
 * algoritmo publico difundido (com o desconto "dsc" quando o 1o DV da 10).
 *
 * <p>Usado como SINAL para o admin, nao como bloqueio: ha variantes do
 * algoritmo em circulacao e um falso negativo impediria um motorista legitimo
 * de se cadastrar. A conferencia final e a imagem da CNH na revisao.
 */
public final class Cnh {

    private Cnh() {}

    public static boolean hasValidCheckDigits(String number) {
        if (number == null || !number.matches("\\d{11}") || number.chars().distinct().count() == 1) {
            return false;
        }
        int sum = 0;
        for (int i = 0, weight = 9; i < 9; i++, weight--) {
            sum += (number.charAt(i) - '0') * weight;
        }
        int dv1 = sum % 11;
        int dsc = 0;
        if (dv1 >= 10) {
            dv1 = 0;
            dsc = 2;
        }
        sum = 0;
        for (int i = 0, weight = 1; i < 9; i++, weight++) {
            sum += (number.charAt(i) - '0') * weight;
        }
        int x = sum % 11;
        int dv2 = x >= 10 ? 0 : x - dsc;
        if (dv2 < 0) {
            dv2 += 11;
        }
        return dv1 == number.charAt(9) - '0' && dv2 == number.charAt(10) - '0';
    }
}
