package com.gayadi.server.favorite.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** FavoritePlaceSaveRequest API 요청 데이터를 전달합니다. */
@Schema(name = "FavoritePlaceSaveRequest", description = "찜한 장소에 저장할 사용자 메모")
public record FavoritePlaceSaveRequest(
        @Schema( description = "사용자가 남길 메모입니다. 생략하거나 null로 보내면 기존 메모를 삭제합니다.", maxLength = 500, requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        @Size(max = 500, message = "{validation.favorite.memo.size}")
        String memo
) {
}
