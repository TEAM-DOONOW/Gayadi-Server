package com.gayadi.server.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** ParticipantRequest API 요청 데이터를 전달합니다. */
@Schema(name = "ParticipantRequest", description = "출발·귀가 장소 설정")
public class ParticipantRequest {

    @Schema(description = "출발 장소 ID", nullable = true, example = "1")
    private Long departurePlaceId;

    @Schema(description = "귀가 장소 ID", nullable = true, example = "2")
    private Long returnPlaceId;

    public Long getDeparturePlaceId() {
        return departurePlaceId;
    }

    public void setDeparturePlaceId(Long departurePlaceId) {
        this.departurePlaceId = departurePlaceId;
    }

    public Long getReturnPlaceId() {
        return returnPlaceId;
    }

    public void setReturnPlaceId(Long returnPlaceId) {
        this.returnPlaceId = returnPlaceId;
    }
}
