package com.gayadi.server.event.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gayadi.server.event.model.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 클라이언트가 등록하는 현장 상황과 관측 상세값을 전달합니다. */
/** 여행 중 관측한 상황 정보를 전달합니다. */
@Schema(name = "EventObservationRequest", description = "여행 중 상황 관측 정보")
public record EventObservationRequest(
        Long placeId,

        @NotBlank(message = "{validation.event.type.required}")
        @Pattern(
                regexp = "WEATHER|CONGESTION|TRANSPORT|CLOSURE|DISASTER",
                message = "{validation.event.type.invalid}")
        String eventType,

        @NotBlank(message = "{validation.event.source.required}")
        @Size(max = 50, message = "{validation.event.source.size}")
        String source,

        @NotNull(message = "{validation.event.severity.required}")
        Severity severity,

        @NotEmpty(message = "{validation.event.values.required}")
        @Size(max = 32, message = "{validation.event.values.size}")
        Map<String, Object> values
) {
}
