package com.unimove.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @Test
    @DisplayName("CPF valido com e sem pontuacao")
    void validCpf() {
        assertThat(Cpf.isValid("529.982.247-25")).isTrue();
        assertThat(Cpf.isValid("52998224725")).isTrue();
    }

    @Test
    @DisplayName("digito verificador errado, tamanho errado, repetido e nulo sao invalidos")
    void invalidCpf() {
        assertThat(Cpf.isValid("529.982.247-24")).isFalse();
        assertThat(Cpf.isValid("5299822472")).isFalse();
        assertThat(Cpf.isValid("111.111.111-11")).isFalse();
        assertThat(Cpf.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("digits remove pontuacao")
    void digits() {
        assertThat(Cpf.digits("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.digits(null)).isNull();
    }
}
