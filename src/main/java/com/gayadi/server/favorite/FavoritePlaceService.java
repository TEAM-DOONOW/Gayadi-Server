package com.gayadi.server.favorite;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.favorite.dto.response.FavoritePlaceResponse;
import com.gayadi.server.favorite.query.FavoritePlaceQueryResult;
import com.gayadi.server.place.PlaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 사용자 찜 장소 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class FavoritePlaceService {

    private static final int MAX_LIST_SIZE = 100;

    private final FavoritePlaceRepository repository;
    private final UserService users;
    private final PlaceService places;

    public FavoritePlaceService(
            FavoritePlaceRepository repository,
            UserService users,
            PlaceService places) {
        this.repository = repository;
        this.users = users;
        this.places = places;
    }

    /** 찜한 장소 조건에 맞는 찜한 장소 정보를 조회합니다. */
    public List<FavoritePlaceResponse> list(long userId) {
        return list(userId, MAX_LIST_SIZE, 0);
    }

    /** 찜한 장소 조건에 맞는 찜한 장소 정보를 조회합니다. */
    public List<FavoritePlaceResponse> list(long userId, int requestedLimit, int requestedOffset) {
        users.requireExists(userId);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int offset = Math.max(0, requestedOffset);
        return repository.findAll(userId, limit, offset).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 찜한 장소 찜한 장소 정보를 등록합니다. */
    @Transactional
    public FavoritePlaceResponse save(long userId, long placeId, String memo) {
        users.requireExists(userId);
        repository.lockUser(userId);
        places.get(placeId);
        repository.upsert(userId, placeId, normalizeMemo(memo));
        return repository.find(userId, placeId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("저장한 찜 장소를 다시 조회하지 못했습니다."));
    }

    /** 찜한 장소 찜한 장소 정보를 삭제합니다. */
    @Transactional
    public void delete(long userId, long placeId) {
        users.requireExists(userId);
        repository.lockUser(userId);
        if (!repository.delete(userId, placeId)) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_PLACE_NOT_FOUND);
        }
    }

    private String normalizeMemo(String memo) {
        return memo == null || memo.isBlank() ? null : memo.trim();
    }

    private FavoritePlaceResponse toResponse(FavoritePlaceQueryResult result) {
        return new FavoritePlaceResponse(
                result.id(),
                result.name(),
                result.category(),
                result.address(),
                result.roadAddress(),
                result.latitude(),
                result.longitude(),
                result.regionId(),
                result.phone(),
                result.homepageUrl(),
                result.imageUrl(),
                result.indoor(),
                result.description(),
                result.memo(),
                result.favoritedAt());
    }
}
