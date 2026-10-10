package com.unimove.shared.util;

/** CPF: normalizacao para so digitos e conferencia dos digitos verificadores. */
public final class Cpf {

    private Cpf() {}

    /** Remove pontuacao; devolve null para entrada nula. */
    public static String digits(String raw) {
        return raw == null ? null : raw.replaceAll("\\D", "");
    }

    public static boolean isValid(String raw) {
        String cpf = digits(raw);
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(cpf, 9) == cpf.charAt(9) - '0'
                && checkDigit(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int checkDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }
        int mod = (sum * 10) % 11;
        return mod == 10 ? 0 : mod;
    }
}
