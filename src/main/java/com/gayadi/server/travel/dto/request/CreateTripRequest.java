package com.gayadi.server.travel.dto.request;

import com.gayadi.server.common.AppDateFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** CreateTripRequest API 요청 데이터를 전달합니다. */
@Schema(name = "CreateTripRequest", description = "새 여행 생성 정보")
public class CreateTripRequest {

    @Schema(description = "여행 이름", example = "서울 당일치기")
    @NotBlank(message = "{validation.trip.name.required}")
    @Size(max = 100, message = "{validation.trip.name.size}")
    private String name;

    @Schema(description = "시작일. yyyy-MM-dd 또는 yyyy.MM.dd", example = "2026-09-20")
    @NotBlank(message = "{validation.trip.start-date.required}")
    @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.trip.date.pattern}")
    private String startDate;

    @Schema(description = "종료일. yyyy-MM-dd 또는 yyyy.MM.dd", example = "2026-09-20")
    @NotBlank(message = "{validation.trip.end-date.required}")
    @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.trip.date.pattern}")
    private String endDate;

    @Schema(description = "여행 도시 목록", example = "[\"서울\"]")
    @NotEmpty(message = "{validation.trip.cities.required}")
    @Size(max = 10, message = "{validation.trip.cities.size}")
    private List<
            @NotBlank(message = "{validation.trip.city.required}")
            @Size(max = 50, message = "{validation.trip.city.size}")
            String> cities;

    @Schema(description = "소유자 출발 장소 ID. 생략하면 나중에 PATCH .../participants/current로 넣을 수 있습니다.",
            nullable = true, example = "1")
    private Long departurePlaceId;

    @Schema(description = "소유자 귀가 장소 ID. 생략하면 나중에 PATCH .../participants/current로 넣을 수 있습니다.",
            nullable = true, example = "1")
    private Long returnPlaceId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public List<String> getCities() {
        return cities;
    }

    public void setCities(List<String> cities) {
        this.cities = cities;
    }

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

    public LocalDate parsedStartDate() {
        return AppDateFormat.parseDate(startDate, "여행 날짜");
    }

    public LocalDate parsedEndDate() {
        return AppDateFormat.parseDate(endDate, "여행 날짜");
    }
}
