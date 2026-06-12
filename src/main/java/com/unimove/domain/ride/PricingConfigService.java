package com.unimove.domain.ride;

import com.unimove.domain.ride.dto.PricingConfigRequest;
import com.unimove.domain.ride.dto.PricingConfigResponse;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PricingConfigService {

    private static final Logger log = LoggerFactory.getLogger(PricingConfigService.class);

    /** Teto de surge default quando o admin nao informa (espelha o default da coluna). */
    private static final BigDecimal DEFAULT_SURGE_CAP = new BigDecimal("1.50");

    private final PricingConfigRepository repository;
    private final PricingPolicy pricingPolicy;
    private final SurgePolicy surgePolicy;

    public PricingConfigService(PricingConfigRepository repository,
                                PricingPolicy pricingPolicy,
                                SurgePolicy surgePolicy) {
        this.repository = repository;
        this.pricingPolicy = pricingPolicy;
        this.surgePolicy = surgePolicy;
    }

    @Transactional(readOnly = true)
    public List<PricingConfigResponse> list() {
        return repository.findAllByOrderByCidadeAscCategoryAsc().stream()
                .map(PricingConfigResponse::from)
                .toList();
    }

    @Transactional
    public PricingConfigResponse upsert(UUID adminId, PricingConfigRequest req) {
        String cidade = PricingConfig.DEFAULT_CIDADE.equals(req.cidade())
                ? PricingConfig.DEFAULT_CIDADE
                : CityNormalizer.normalize(req.cidade());

        PricingConfig cfg = repository.findByCidadeAndCategory(cidade, req.category())
                .orElseGet(PricingConfig::new);
        cfg.setCidade(cidade);
        cfg.setCategory(req.category());
        cfg.setBase(req.base());
        cfg.setPerKm(req.perKm());
        cfg.setPerMin(req.perMin());
        cfg.setSurgeEnabled(Boolean.TRUE.equals(req.surgeEnabled()));
        cfg.setSurgeCap(req.surgeCap() != null ? req.surgeCap() : DEFAULT_SURGE_CAP);
        cfg.setUpdatedByAdminId(adminId);
        PricingConfig saved = repository.save(cfg);

        pricingPolicy.reload();
        surgePolicy.reload();
        log.info("Pricing config upsert por admin {}: cidade={} category={} base={} perKm={} perMin={} surge={} cap={}",
                adminId, saved.getCidade(), saved.getCategory(),
                saved.getBase(), saved.getPerKm(), saved.getPerMin(),
                saved.isSurgeEnabled(), saved.getSurgeCap());
        return PricingConfigResponse.from(saved);
    }

    @Transactional
    public void delete(UUID adminId, String cidade, RideCategory category) {
        String resolved = PricingConfig.DEFAULT_CIDADE.equals(cidade)
                ? PricingConfig.DEFAULT_CIDADE
                : CityNormalizer.normalize(cidade);

        if (PricingConfig.DEFAULT_CIDADE.equals(resolved)) {
            throw new CannotDeleteDefaultPricingException();
        }

        PricingConfig cfg = repository.findByCidadeAndCategory(resolved, category)
                .orElseThrow(PricingConfigNotFoundException::new);
        repository.delete(cfg);

        pricingPolicy.reload();
        surgePolicy.reload();
        log.info("Pricing config removida por admin {}: cidade={} category={}", adminId, resolved, category);
    }
}
