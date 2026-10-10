package com.unimove.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VehiclePlateTest {

    @Test
    void normalizeRemovesHyphenSpacesAndUppercases() {
        assertThat(VehiclePlate.normalize(" abc-1234 ")).isEqualTo("ABC1234");
        assertThat(VehiclePlate.normalize("abc1d23")).isEqualTo("ABC1D23");
        assertThat(VehiclePlate.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC1234", "abc-1234", "ABC1D23", " ABC1D23 "})
    void patternAcceptsOldAndMercosul(String plate) {
        assertThat(plate.matches(VehiclePlate.PATTERN)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB1234", "ABCD123", "ABC 1234", "ABC--1234", "1234ABC"})
    void patternRejectsMalformed(String plate) {
        assertThat(plate.matches(VehiclePlate.PATTERN)).isFalse();
    }
}
