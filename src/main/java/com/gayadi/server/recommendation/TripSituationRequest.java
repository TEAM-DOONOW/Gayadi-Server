package com.gayadi.server.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

public class TripSituationRequest {

    @Size(max = 10)
    @Schema(description = "법정동 시도 코드", example = "11")
    private String regionCode = "";

    @Size(max = 10)
    @Schema(description = "법정동 시군구 코드(3자리 또는 시도 포함 5자리)", example = "110")
    private String sigunguCode = "";

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(description = "현재 또는 기준 위도", example = "35.5384")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Schema(description = "현재 또는 기준 경도", example = "129.3114")
    private Double longitude;

    @Size(max = 10)
    private List<@Size(max = 50) String> keywords = List.of();

    @Min(1)
    @Max(PlaceRecommendationRequest.MAX_RECOMMENDATIONS)
    private int limit = 5;

    @Size(max = 40)
    private String targetAt = "";

    @NotNull
    @Valid
    @Schema(description = "날씨·혼잡·교통 상황. 날씨와 혼잡을 생략하면 공공데이터 또는 명시된 추정값으로 자동 보강")
    private TravelSituation situation = TravelSituation.empty();

    @AssertTrue(message = "외부 맞춤 추천 처리에 동의해야 합니다.")
    private boolean externalProcessingConsent;

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getSigunguCode() { return sigunguCode; }
    public void setSigunguCode(String sigunguCode) { this.sigunguCode = sigunguCode; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public String getTargetAt() { return targetAt; }
    public void setTargetAt(String targetAt) { this.targetAt = targetAt; }
    public TravelSituation getSituation() {
        return situation == null ? TravelSituation.empty() : situation;
    }
    public void setSituation(TravelSituation situation) { this.situation = situation; }
    public boolean isExternalProcessingConsent() { return externalProcessingConsent; }
    public void setExternalProcessingConsent(boolean externalProcessingConsent) {
        this.externalProcessingConsent = externalProcessingConsent;
    }

    @AssertTrue(message = "추천 대상 시각은 UTC 오프셋을 포함한 ISO-8601 형식이어야 합니다.")
    public boolean isTargetAtValid() {
        if (targetAt == null || targetAt.isBlank()) return true;
        try {
            OffsetDateTime.parse(targetAt);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
