package com.gayadi.server.recommendation;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class PlaceRecommendationRequest {

    @NotBlank
    private String profile;
    private double latitude;
    private double longitude;
    private List<String> keywords = List.of();
    private int limit = 5;

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}
