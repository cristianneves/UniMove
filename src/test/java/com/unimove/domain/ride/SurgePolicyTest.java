package com.unimove.domain.ride;

import com.unimove.domain.user.DriverService;
import com.unimove.domain.user.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SurgePolicy — ladder, teto e gating")
class SurgePolicyTest {

    private static final String CIDADE = "remanso";

    @Mock PricingConfigRepository repository;
    @Mock RideRepository rideRepository;
    @Mock DriverService driverService;

    SurgePolicy surgePolicy;

    @BeforeEach
    void setUp() {
        surgePolicy = new SurgePolicy(repository, rideRepository, driverService);
    }

    private PricingConfig config(String cidade, RideCategory category, boolean enabled, String cap) {
        PricingConfig c = new PricingConfig();
        c.setCidade(cidade);
        c.setCategory(category);
        c.setBase(BigDecimal.ONE);
        c.setPerKm(BigDecimal.ONE);
        c.setPerMin(BigDecimal.ONE);
        c.setSurgeEnabled(enabled);
        c.setSurgeCap(new BigDecimal(cap));
        return c;
    }

    /** Carrega o cache e stuba demanda/oferta para produzir o ratio desejado. */
    private void given(boolean enabled, String cap, long demand, long online, long busy) {
        when(repository.findAll()).thenReturn(List.of(config(CIDADE, RideCategory.CARRO, enabled, cap)));
        surgePolicy.reload();
        lenient().when(rideRepository.countByStatusAndCidadeAndCategory(
                eq(RideStatus.AVAILABLE_IN_MURAL), eq(CIDADE), eq(RideCategory.CARRO))).thenReturn(demand);
        lenient().when(driverService.countOnline(eq(CIDADE), eq(VehicleType.CARRO))).thenReturn(online);
        lenient().when(rideRepository.countBusyDrivers(eq(CIDADE), eq(RideCategory.CARRO), anyCollection()))
                .thenReturn(busy);
    }

    private BigDecimal multiplier() {
        return surgePolicy.multiplier(CIDADE, RideCategory.CARRO);
    }

    @Test
    @DisplayName("ratio < 1.0 → sem surge (1.00)")
    void ratioBelowOne() {
        given(true, "1.50", 1, 2, 0); // supply=2, ratio=0.5
        assertThat(multiplier()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("1.0 ≤ ratio < 1.5 → 1.20")
    void ratioFirstTier() {
        given(true, "1.50", 4, 3, 0); // supply=3, ratio≈1.33
        assertThat(multiplier()).isEqualByComparingTo("1.20");
    }

    @Test
    @DisplayName("1.5 ≤ ratio < 2.0 → 1.35")
    void ratioSecondTier() {
        given(true, "1.50", 3, 2, 0); // supply=2, ratio=1.5
        assertThat(multiplier()).isEqualByComparingTo("1.35");
    }

    @Test
    @DisplayName("ratio ≥ 2.0 → teto (1.50)")
    void ratioTopTier() {
        given(true, "1.50", 4, 2, 0); // supply=2, ratio=2.0
        assertThat(multiplier()).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("oferta zero com demanda → teto direto")
    void noSupplyGoesToCap() {
        given(true, "1.50", 1, 1, 1); // online=1, busy=1 → supply=0
        assertThat(multiplier()).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("teto baixo clampa o degrau (cap 1.30 < 1.50)")
    void capClampsTier() {
        given(true, "1.30", 4, 2, 0); // ratio=2.0 → degrau 1.50, clampado em 1.30
        assertThat(multiplier()).isEqualByComparingTo("1.30");
    }

    @Test
    @DisplayName("surge desligado → 1.00 mesmo com demanda alta")
    void disabledReturnsNoSurge() {
        given(false, "1.50", 10, 1, 0);
        assertThat(multiplier()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("sem demanda → 1.00 (nao toca a oferta)")
    void noDemandReturnsNoSurge() {
        given(true, "1.50", 0, 1, 0);
        assertThat(multiplier()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("cidade sem config → 1.00 (fallback)")
    void unknownCityReturnsNoSurge() {
        when(repository.findAll()).thenReturn(List.of(config(CIDADE, RideCategory.CARRO, true, "1.50")));
        surgePolicy.reload();
        assertThat(surgePolicy.multiplier("cidade-sem-config", RideCategory.CARRO))
                .isEqualByComparingTo("1.00");
    }
}
