package com.unimove.domain.maps;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteHasherTest {

    @Test
    void mesmasCoordenadasGeramMesmoHash() {
        String a = RouteHasher.hash(-20.81972, -49.37944, -20.79500, -49.36000);
        String b = RouteHasher.hash(-20.81972, -49.37944, -20.79500, -49.36000);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void diferencaAlemDaQuartaCasaNaoAlteraOHash() {
        // Quinta casa decimal (~1m) deve ser arredondada e ignorada.
        String base = RouteHasher.hash(-20.81972, -49.37944, -20.79500, -49.36000);
        String ruido = RouteHasher.hash(-20.81973, -49.37944, -20.79500, -49.36000);
        assertThat(ruido).isEqualTo(base);
    }

    @Test
    void diferencaNaQuartaCasaAlteraOHash() {
        String base = RouteHasher.hash(-20.81972, -49.37944, -20.79500, -49.36000);
        String outro = RouteHasher.hash(-20.81982, -49.37944, -20.79500, -49.36000);
        assertThat(outro).isNotEqualTo(base);
    }

    @Test
    void invertendoOrigemEDestinoGeraHashDiferente() {
        String ida = RouteHasher.hash(-20.81972, -49.37944, -20.79500, -49.36000);
        String volta = RouteHasher.hash(-20.79500, -49.36000, -20.81972, -49.37944);
        assertThat(ida).isNotEqualTo(volta);
    }

    @Test
    void hashTemTamanhoHexSha256() {
        String h = RouteHasher.hash(0.0, 0.0, 1.0, 1.0);
        assertThat(h).hasSize(64).matches("[0-9a-f]+");
    }
}
