package com.unimove.domain.city.dto;

/**
 * Visao do admin. O {@code userCount} existe para que desativar uma cidade nao
 * seja uma decisao as cegas — ele diz quantas contas continuarao operando ali.
 */
public record AdminCityItem(String slug, String displayName, boolean active, long userCount) {
}
