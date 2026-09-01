package com.gayadi.server.recommendation;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.recommendation.dto.response.EmbeddingRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 장소 임베딩을 관리자가 갱신하는 HTTP 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "관리", description = "운영에 필요한 장소 자료를 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class EmbeddingAdminController {

    private final ObjectProvider<PlaceEmbeddingService> serviceProvider;
    private final long adminUserId;

    public EmbeddingAdminController(
            ObjectProvider<PlaceEmbeddingService> serviceProvider,
            @Value("${app.admin.user-id:0}") long adminUserId) {
        this.serviceProvider = serviceProvider;
        this.adminUserId = adminUserId;
    }

    @PostMapping("/place-embeddings")
    @Operation(summary = "장소 검색 자료 갱신")
    public EmbeddingRefreshResponse embedPlaces(@AuthenticationPrincipal Long userId) {
        if (adminUserId <= 0 || userId == null || userId.longValue() != adminUserId) {
            throw new BusinessException(RecommendationErrorCode.EMBEDDING_ADMIN_FORBIDDEN);
        }
        PlaceEmbeddingService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(RecommendationErrorCode.EMBEDDING_UNAVAILABLE);
        }
        int count = service.embedAllPlaces();
        return new EmbeddingRefreshResponse(
                "완료",
                count,
                "장소 임베딩이 완료되었습니다.");
    }
}
