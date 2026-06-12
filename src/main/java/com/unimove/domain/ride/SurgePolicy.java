package com.unimove.domain.ride;

import com.unimove.domain.user.DriverService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Surge pricing: multiplicador dinamico aplicado sobre a tarifa base quando a
 * demanda supera a oferta de motoristas, por (cidade, category).
 *
 * Sinal = demanda / oferta:
 *   demanda = corridas aguardando no mural (AVAILABLE_IN_MURAL) da cidade+categoria;
 *   oferta  = motoristas online da cidade+categoria MENOS os ja em corrida ativa.
 *
 * A curva e em degraus (ladder fixa, regra de produto estavel); o admin so controla
 * o liga/desliga e o teto por cidade+categoria (espelha o cache da PricingPolicy,
 * recarregado no upsert). Calculo roda FORA de transacao, como o estimate.
 */
@Component
public class SurgePolicy {

    private static final Logger log = LoggerFactory.getLogger(SurgePolicy.class);

    private static final BigDecimal NO_SURGE = BigDecimal.ONE.setScale(2);

    /** Estados em que o motorista esta comprometido (indisponivel como oferta). */
    private static final List<RideStatus> DRIVER_BUSY_STATUSES =
            List.of(RideStatus.DRIVER_EN_ROUTE, RideStatus.IN_PROGRESS);

    /** Degraus ratio -> multiplicador. O ultimo degrau e clampado pelo teto (surgeCap). */
    private record Tier(BigDecimal minRatio, BigDecimal multiplier) {}

    private static final List<Tier> LADDER = List.of(
            new Tier(new BigDecimal("2.0"), new BigDecimal("1.50")),
            new Tier(new BigDecimal("1.5"), new BigDecimal("1.35")),
            new Tier(new BigDecimal("1.0"), new BigDecimal("1.20"))
    );

    private final PricingConfigRepository repository;
    private final RideRepository rideRepository;
    private final DriverService driverService;

    private volatile Map<Key, Config> cache = Map.of();

    public SurgePolicy(PricingConfigRepository repository,
                       RideRepository rideRepository,
                       DriverService driverService) {
        this.repository = repository;
        this.rideRepository = rideRepository;
        this.driverService = driverService;
    }

    @PostConstruct
    public void reload() {
        Map<Key, Config> next = new HashMap<>();
        repository.findAll().forEach(c -> next.put(
                new Key(c.getCidade(), c.getCategory()),
                new Config(c.isSurgeEnabled(), c.getSurgeCap())
        ));
        this.cache = Map.copyOf(next);
        log.info("SurgePolicy cache carregado ({} entradas)", cache.size());
    }

    /**
     * Multiplicador vigente para (cidade, category). Retorna 1.00 quando o surge
     * esta desligado, sem demanda, ou sem config — nunca abaixo de 1.00.
     */
    public BigDecimal multiplier(String cidade, RideCategory category) {
        Config cfg = resolve(cidade, category);
        if (cfg == null || !cfg.enabled()) {
            return NO_SURGE;
        }

        long demand = rideRepository.countByStatusAndCidadeAndCategory(
                RideStatus.AVAILABLE_IN_MURAL, cidade, category);
        if (demand <= 0) {
            return NO_SURGE;
        }

        long online = driverService.countOnline(cidade, category.toVehicleType());
        long busy = rideRepository.countBusyDrivers(cidade, category, DRIVER_BUSY_STATUSES);
        long supply = Math.max(0, online - busy);

        BigDecimal tierMultiplier = supply == 0
                ? cfg.cap()  // demanda sem nenhum motorista livre: teto direto
                : ladder(BigDecimal.valueOf(demand).divide(BigDecimal.valueOf(supply), 4, java.math.RoundingMode.HALF_UP));

        // Teto: nunca acima do surge_cap configurado para a cidade.
        BigDecimal capped = tierMultiplier.min(cfg.cap()).setScale(2, java.math.RoundingMode.HALF_UP);
        if (capped.compareTo(NO_SURGE) > 0) {
            log.info("Surge {} cidade={} category={} (demanda={}, oferta={})",
                    capped, cidade, category, demand, supply);
        }
        return capped;
    }

    private BigDecimal ladder(BigDecimal ratio) {
        for (Tier t : LADDER) {
            if (ratio.compareTo(t.minRatio()) >= 0) {
                return t.multiplier();
            }
        }
        return NO_SURGE;
    }

    private Config resolve(String cidade, RideCategory category) {
        Map<Key, Config> local = cache;
        Config c = local.get(new Key(cidade, category));
        if (c != null) {
            return c;
        }
        return local.get(new Key(PricingConfig.DEFAULT_CIDADE, category));
    }

    private record Key(String cidade, RideCategory category) {}
    private record Config(boolean enabled, BigDecimal cap) {}
}
