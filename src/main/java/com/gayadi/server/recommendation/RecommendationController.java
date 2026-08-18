package com.gayadi.server.recommendation;

import com.gayadi.server.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "추천", description = "여행 성향과 위치에 맞는 장소를 추천합니다.")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final ObjectProvider<RecommendationService> serviceProvider;

    public RecommendationController(ObjectProvider<RecommendationService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @PostMapping("/places")
    @Operation(summary = "맞춤 장소 추천")
    public PlaceRecommendationResponse recommendPlaces(@Valid @RequestBody PlaceRecommendationRequest request) {
        RecommendationService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "맞춤 장소 추천 기능이 설정되지 않았습니다.");
        }
        return service.recommendPlaces(request);
    }
}
