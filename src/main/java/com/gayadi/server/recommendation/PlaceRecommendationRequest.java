package com.gayadi.server.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

public class PlaceRecommendationRequest {

    public static final String PURPOSE_PLACE_RECOMMENDATION = "PLACE_RECOMMENDATION";
    public static final String PURPOSE_PLAN_GENERATION = "PLAN_GENERATION";
    public static final String PURPOSE_SITUATION_RESPONSE = "SITUATION_RESPONSE";
    public static final int MAX_RECOMMENDATIONS = 20;

    @Size(max = 100)
    @Schema(description = "여행 목적지", example = "울산")
    private String destination = "";

    @Size(max = 10)
    @Schema(description = "한국관광공사 시도 코드", example = "31")
    private String regionCode = "";

    @Size(max = 10)
    @Schema(description = "한국관광공사 시군구 코드")
    private String sigunguCode = "";

    @Pattern(regexp = PURPOSE_PLACE_RECOMMENDATION + "|" + PURPOSE_PLAN_GENERATION
            + "|" + PURPOSE_SITUATION_RESPONSE,
            message = "추천 목적이 올바르지 않습니다.")
    @Schema(description = "추천 목적", example = "PLACE_RECOMMENDATION")
    private String purpose = PURPOSE_PLACE_RECOMMENDATION;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "여행 성향 설명", example = "조용한 자연 공간과 여유 있는 일정을 좋아합니다.")
    private String profile;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(description = "현재 위도", example = "37.5665")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Schema(description = "현재 경도", example = "126.9780")
    private Double longitude;

    @Size(max = 10)
    @Schema(description = "원하는 장소를 나타내는 낱말")
    private List<@NotBlank @Size(max = 50) String> keywords = List.of();

    @Min(1)
    @Max(MAX_RECOMMENDATIONS)
    @Schema(description = "추천받을 장소 수", example = "5")
    private int limit = 5;

    @Min(1)
    @Max(100)
    @Schema(description = "여행에 참여하는 실제 인원 수", example = "5")
    private int groupSize = 1;

    @Size(max = 40)
    @Schema(description = "추천 대상 시각(ISO-8601)", example = "2026-08-22T14:00:00+09:00")
    private String targetAt = "";

    @Schema(description = "날씨·혼잡·교통 상황 입력")
    @Valid
    private TravelSituation situation = TravelSituation.empty();

    @AssertTrue(message = "외부 맞춤 추천 처리에 동의해야 합니다.")
    @Schema(description = "성향과 검색어를 외부 추천 모델에서 처리하는 데 동의하는지", example = "true")
    private boolean externalProcessingConsent;

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getSigunguCode() { return sigunguCode; }
    public void setSigunguCode(String sigunguCode) { this.sigunguCode = sigunguCode; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }
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
        return validOffsetDateTime(targetAt);
    }

    private boolean validOffsetDateTime(String value) {
        if (value == null || value.isBlank()) return true;
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
