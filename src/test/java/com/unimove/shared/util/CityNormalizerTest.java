package com.unimove.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CityNormalizerTest {

    @Test
    void normalizeRemovesAccentsLowercasesAndHyphenates() {
        assertThat(CityNormalizer.normalize("São José do Rio Preto")).isEqualTo("sao-jose-do-rio-preto");
        assertThat(CityNormalizer.normalize("Campinas")).isEqualTo("campinas");
        assertThat(CityNormalizer.normalize("  Ribeirão Preto  ")).isEqualTo("ribeirao-preto");
        assertThat(CityNormalizer.normalize("Sant'Ana")).isEqualTo("sant-ana");
    }

    @Test
    void normalizeNullReturnsNull() {
        assertThat(CityNormalizer.normalize(null)).isNull();
    }
}
