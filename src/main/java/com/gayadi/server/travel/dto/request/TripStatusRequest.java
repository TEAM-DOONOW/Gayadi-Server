package com.gayadi.server.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** TripStatusRequest API 요청 데이터를 전달합니다. */
@Schema(name = "TripStatusRequest", description = "여행 상태 변경 정보")
public class TripStatusRequest {

    @NotNull(message = "{validation.trip.status.required}")
    @Pattern(regexp = "PLANNING|ONGOING|IN_PROGRESS|COMPLETED",
            message = "{validation.trip.status.pattern}")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
