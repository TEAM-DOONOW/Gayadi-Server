package com.gayadi.server.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** ParticipantRequest API 요청 데이터를 전달합니다. */
@Schema(name = "ParticipantRequest", description = "여행 참여자 변경 정보")
public class ParticipantRequest {

    private Long departurePlaceId;

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
