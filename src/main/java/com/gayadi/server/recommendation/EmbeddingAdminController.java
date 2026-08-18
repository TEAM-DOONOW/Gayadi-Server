package com.gayadi.server.recommendation;

import com.gayadi.server.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public Map<String, Object> embedPlaces(@AuthenticationPrincipal Long userId) {
        if (adminUserId <= 0 || userId == null || userId.longValue() != adminUserId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "관리자만 장소 검색 자료를 갱신할 수 있습니다.");
        }
        PlaceEmbeddingService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "장소 검색 자료 갱신 기능이 설정되지 않았습니다.");
        }
        int count = service.embedAllPlaces();
        return Map.of("status", "완료", "embeddedCount", count,
                "message", "장소 임베딩이 완료되었습니다.");
    }
}
