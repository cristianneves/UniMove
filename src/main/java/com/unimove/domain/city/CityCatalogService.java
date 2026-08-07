package com.unimove.domain.city;

import com.unimove.domain.city.dto.AdminCityItem;
import com.unimove.domain.city.dto.CityItem;
import com.unimove.domain.user.UserDirectory;
import com.unimove.shared.util.CityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lista fechada de cidades atendidas. Sem cache: e um SELECT por chave primaria
 * numa tabela de dezenas de linhas, chamado apenas nos caminhos frios (cadastro
 * e edicao de perfil) — cache aqui so criaria invalidacao para manter.
 */
@Service
public class CityCatalogService implements CityCatalog {

    private static final Logger log = LoggerFactory.getLogger(CityCatalogService.class);

    private final ServedCityRepository repository;
    private final UserDirectory userDirectory;

    public CityCatalogService(ServedCityRepository repository, UserDirectory userDirectory) {
        this.repository = repository;
        this.userDirectory = userDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public void assertServed(String slug) {
        if (!repository.existsBySlugAndActiveTrue(slug)) {
            throw new CityNotServedException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CityItem> listActive() {
        return repository.findAllByActiveTrueOrderByDisplayNameAsc().stream()
                .map(c -> new CityItem(c.getSlug(), c.getDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCityItem> listAll() {
        return repository.findAllByOrderByDisplayNameAsc().stream()
                .map(c -> new AdminCityItem(c.getSlug(), c.getDisplayName(), c.isActive(),
                        userDirectory.countByCidade(c.getSlug())))
                .toList();
    }

    /**
     * Abre uma cidade. Upsert por slug: reabrir uma cidade desativada reativa a
     * linha e atualiza o nome de exibicao, em vez de duplicar — o slug e a
     * identidade, e ele ja esta gravado em {@code users.cidade} e
     * {@code rides.cidade} de quem opera ali.
     */
    @Transactional
    public OpenResult open(UUID adminId, String cidade) {
        String slug = CityNormalizer.normalize(cidade);
        if (slug == null || slug.isEmpty()) {
            throw new InvalidCityNameException();
        }

        ServedCity city = repository.findById(slug).orElse(null);
        boolean created = city == null;
        if (created) {
            city = new ServedCity();
            city.setSlug(slug);
        }
        city.setDisplayName(cidade.trim());
        city.setActive(true);
        repository.save(city);

        log.info("Cidade {} ({}) aberta pelo admin {} ({})",
                slug, city.getDisplayName(), adminId, created ? "nova" : "reativada");
        return new OpenResult(toAdminItem(city), created);
    }

    /**
     * Desativa (soft). Quem ja esta na cidade continua operando — a validacao
     * roda so na escrita —, apenas ninguem novo a escolhe. Remover de verdade
     * exigiria migrar ou travar essas contas, sem ganho no MVP.
     */
    @Transactional
    public void deactivate(UUID adminId, String slug) {
        // Normaliza tambem aqui: o admin normalmente cola o slug da listagem,
        // mas digitar "Casa Nova/BA" na URL nao deveria dar 404 enganoso.
        String resolved = CityNormalizer.normalize(slug);
        ServedCity city = repository.findById(resolved == null ? "" : resolved)
                .orElseThrow(CityNotFoundException::new);
        city.setActive(false);
        log.info("Cidade {} desativada pelo admin {} ({} contas seguem ativas ali)",
                resolved, adminId, userDirectory.countByCidade(resolved));
    }

    private AdminCityItem toAdminItem(ServedCity city) {
        return new AdminCityItem(city.getSlug(), city.getDisplayName(), city.isActive(),
                userDirectory.countByCidade(city.getSlug()));
    }

    /** {@code created} distingue 201 (cidade nova) de 200 (reativada). */
    public record OpenResult(AdminCityItem city, boolean created) {}
}
