package com.unimove.domain.user.dto;

import com.unimove.domain.chat.dto.ChatMessageResponse;
import com.unimove.domain.ride.dto.RideHistoryItem;
import com.unimove.domain.user.document.dto.DriverDocumentationResponse;

import java.time.Instant;
import java.util.List;

/**
 * Copia dos dados pessoais do titular (LGPD art. 18, II). Arquivos de
 * documento nao vem embutidos — so os metadados da analise; o arquivo em si
 * e entregue pelo suporte mediante solicitacao.
 */
public record DataExportResponse(
        Instant generatedAt,
        UserProfileResponse profile,
        PrivacyPreferencesResponse preferences,
        List<SavedPlaceResponse> savedPlaces,
        List<RideHistoryItem> rides,
        List<ChatMessageResponse> chatMessagesSent,
        DriverDocumentationResponse driverDocumentation
) {}
