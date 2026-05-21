package com.unimove.domain.user;

import com.unimove.domain.user.dto.CreateSavedPlaceRequest;
import com.unimove.domain.user.dto.SavedPlaceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SavedPlaceService {

    private static final Logger log = LoggerFactory.getLogger(SavedPlaceService.class);

    private final SavedPlaceRepository repository;

    public SavedPlaceService(SavedPlaceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SavedPlaceResponse create(UUID userId, CreateSavedPlaceRequest req) {
        String label = req.label().trim();
        if (repository.existsByUserIdAndLabel(userId, label)) {
            throw new DuplicateSavedPlaceLabelException(label);
        }

        SavedPlace place = new SavedPlace();
        place.setUserId(userId);
        place.setLabel(label);
        place.setAddress(req.address().trim());
        place.setLat(req.lat());
        place.setLng(req.lng());

        SavedPlace saved = repository.save(place);
        log.info("SavedPlace {} criado para user {} (label={})", saved.getId(), userId, label);

        return new SavedPlaceResponse(
                saved.getId(), saved.getLabel(), saved.getAddress(),
                saved.getLat(), saved.getLng(), saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<SavedPlaceResponse> list(UUID userId) {
        return repository.findAllByUser(userId);
    }

    @Transactional
    public void delete(UUID userId, UUID placeId) {
        SavedPlace place = repository.findByIdAndUserId(placeId, userId)
                .orElseThrow(SavedPlaceNotFoundException::new);
        repository.delete(place);
        log.info("SavedPlace {} removido por user {}", placeId, userId);
    }
}
