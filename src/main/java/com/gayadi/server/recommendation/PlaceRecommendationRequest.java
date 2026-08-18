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

import java.util.List;

public class PlaceRecommendationRequest {

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
    @Max(20)
    @Schema(description = "추천받을 장소 수", example = "5")
    private int limit = 5;

    @AssertTrue(message = "외부 맞춤 추천 처리에 동의해야 합니다.")
    @Schema(description = "성향과 검색어를 외부 추천 모델에서 처리하는 데 동의하는지", example = "true")
    private boolean externalProcessingConsent;

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public boolean isExternalProcessingConsent() { return externalProcessingConsent; }
    public void setExternalProcessingConsent(boolean externalProcessingConsent) {
        this.externalProcessingConsent = externalProcessingConsent;
    }
}
