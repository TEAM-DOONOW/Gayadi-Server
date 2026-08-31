package com.gayadi.server.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/** UpdateTripRequest API 요청 데이터를 전달합니다. */
@Schema(name = "UpdateTripRequest", description = "여행 기본 정보 수정 요청")
public class UpdateTripRequest extends CreateTripRequest {

    @PositiveOrZero(message = "{validation.trip.version.positive-or-zero}")
    private Integer version;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
