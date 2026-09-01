package com.gayadi.server.favorite;

import com.gayadi.server.favorite.dto.request.FavoritePlaceSaveRequest;
import com.gayadi.server.favorite.dto.response.FavoritePlaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사용자 찜 장소 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1/users/current/favorite-places")
@Tag(name = "찜", description = "현재 사용자가 저장한 장소를 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class FavoritePlaceController {

    private final FavoritePlaceService service;

    public FavoritePlaceController(FavoritePlaceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "찜한 장소 목록")
    @ApiResponse(responseCode = "200", description = "현재 사용자가 찜한 장소 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = FavoritePlaceResponse.class))))
    public List<FavoritePlaceResponse> favoritePlaces(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.list(userId, limit, offset);
    }

    @PutMapping("/{placeId}")
    @Operation(summary = "장소 찜 저장")
    @ApiResponse(responseCode = "200", description = "저장한 찜 장소입니다.",
            content = @Content(schema = @Schema(implementation = FavoritePlaceResponse.class)))
    public FavoritePlaceResponse favoritePlace(
            @PathVariable long placeId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody(required = false) FavoritePlaceSaveRequest request) {
        return service.save(userId, placeId, request == null ? null : request.memo());
    }

    @DeleteMapping("/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "장소 찜 삭제")
    public void favoritePlace(
            @PathVariable long placeId,
            @AuthenticationPrincipal Long userId) {
        service.delete(userId, placeId);
    }
}
