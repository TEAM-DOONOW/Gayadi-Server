package com.gayadi.server.recommendation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' != ''")
public class EmbeddingAdminController {

    private final PlaceEmbeddingService service;

    public EmbeddingAdminController(PlaceEmbeddingService service) {
        this.service = service;
    }

    @PostMapping("/embed-places")
    public Map<String, Object> embedPlaces() {
        int count = service.embedAllPlaces();
        return Map.of("status", "completed", "embeddedCount", count,
                "message", "장소 임베딩이 완료되었습니다.");
    }
}
