package com.unimove.domain.ride;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolve a tarifa consultando pricing_configs (cidade, category) com
 * fallback para (_DEFAULT, category). Cache em memoria recarregado no
 * startup e invalidado quando o ADMIN edita via AdminPricingController.
 *
 * Constantes hardcoded existem apenas como ultima rede de seguranca:
 * se alguem deletar o _DEFAULT por engano, o app nao quebra.
 */
@Component
public class PricingPolicy {

    private static final Logger log = LoggerFactory.getLogger(PricingPolicy.class);

    private static final BigDecimal SAFETY_BASE_CARRO    = new BigDecimal("5.50");
    private static final BigDecimal SAFETY_PER_KM_CARRO  = new BigDecimal("2.10");
    private static final BigDecimal SAFETY_PER_MIN_CARRO = new BigDecimal("0.20");
    private static final BigDecimal SAFETY_BASE_MOTO     = new BigDecimal("3.85");
    private static final BigDecimal SAFETY_PER_KM_MOTO   = new BigDecimal("1.47");
    private static final BigDecimal SAFETY_PER_MIN_MOTO  = new BigDecimal("0.14");

    private final PricingConfigRepository repository;

    private volatile Map<Key, Snapshot> cache = Map.of();

    public PricingPolicy(PricingConfigRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        Map<Key, Snapshot> next = new HashMap<>();
        repository.findAll().forEach(c -> next.put(
                new Key(c.getCidade(), c.getCategory()),
                new Snapshot(c.getBase(), c.getPerKm(), c.getPerMin())
        ));
        this.cache = Map.copyOf(next);
        log.info("PricingPolicy cache carregado ({} entradas)", cache.size());
    }

    public BigDecimal calculate(BigDecimal distanciaKm, int tempoMin, RideCategory category, String cidade) {
        Snapshot s = resolve(cidade, category);
        BigDecimal byDistance = s.perKm.multiply(distanciaKm);
        BigDecimal byTime = s.perMin.multiply(BigDecimal.valueOf(tempoMin));
        return s.base.add(byDistance).add(byTime).setScale(2, RoundingMode.HALF_UP);
    }

    private Snapshot resolve(String cidade, RideCategory category) {
        Map<Key, Snapshot> local = cache;
        Snapshot s = local.get(new Key(cidade, category));
        if (s != null) {
            return s;
        }
        s = local.get(new Key(PricingConfig.DEFAULT_CIDADE, category));
        if (s != null) {
            return s;
        }
        log.warn("Pricing config ausente para cidade={} category={} — usando fallback hardcoded",
                cidade, category);
        return switch (category) {
            case CARRO -> new Snapshot(SAFETY_BASE_CARRO, SAFETY_PER_KM_CARRO, SAFETY_PER_MIN_CARRO);
            case MOTO  -> new Snapshot(SAFETY_BASE_MOTO,  SAFETY_PER_KM_MOTO,  SAFETY_PER_MIN_MOTO);
        };
    }

    private record Key(String cidade, RideCategory category) {}
    private record Snapshot(BigDecimal base, BigDecimal perKm, BigDecimal perMin) {}
}
