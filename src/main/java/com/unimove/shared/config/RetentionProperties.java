package com.unimove.shared.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Prazos de retencao (LGPD, ver docs/lgpd-inventario-dados.md). Cada dominio
 * tem seu job; os prazos ficam juntos aqui para a politica ser uma so.
 *
 * @param enabled               liga/desliga os expurgos.
 * @param rideYears             corridas mais velhas que isso perdem endereco, rota e
 *                              coordenadas precisas (ficam so o agregado).
 * @param rejectedDocumentDays  arquivo de documento reprovado ou substituido e apagado
 *                              do storage depois disso (o registro da analise fica).
 * @param cron                  quando rodar (padrao: todo dia as 03:30, horario de Brasilia).
 */
@Validated
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") @Positive int rideYears,
        @DefaultValue("90") @Positive int rejectedDocumentDays,
        @DefaultValue("0 30 3 * * *") String cron
) {}
