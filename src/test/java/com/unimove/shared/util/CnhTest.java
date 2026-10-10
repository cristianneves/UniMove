package com.unimove.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CnhTest {

    @Test
    @DisplayName("numeros com digitos verificadores corretos (inclusive o caso com desconto dsc)")
    void validNumbers() {
        // base 123456789: soma1=165 → dv1=0; soma2=285 → dv2=10 → 0
        assertThat(Cnh.hasValidCheckDigits("12345678900")).isTrue();
        // base 000000019: soma1=11 → dv1=0; soma2=89 → dv2=1
        assertThat(Cnh.hasValidCheckDigits("00000001901")).isTrue();
        // base 000000050: soma1=10 → dv1=0 com desconto 2; soma2=40 → 7-2 = 5
        assertThat(Cnh.hasValidCheckDigits("00000005005")).isTrue();
    }

    @Test
    @DisplayName("digito errado, formato errado e repetidos sao invalidos")
    void invalidNumbers() {
        assertThat(Cnh.hasValidCheckDigits("12345678901")).isFalse();
        assertThat(Cnh.hasValidCheckDigits("1234567890")).isFalse();
        assertThat(Cnh.hasValidCheckDigits("1234567890a")).isFalse();
        assertThat(Cnh.hasValidCheckDigits("11111111111")).isFalse();
        assertThat(Cnh.hasValidCheckDigits(null)).isFalse();
    }
}
