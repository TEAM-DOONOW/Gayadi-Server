package com.gayadi.server;

import com.gayadi.server.recommendation.PlaceEmbeddingService;
import com.gayadi.server.recommendation.RecommendationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "app.ai.enabled=true",
        "app.ai.embedding.enabled=true",
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=openai",
        "spring.ai.openai.api-key=test-key"
})
class AiEnabledContextTests {

    @Autowired
    ApplicationContext context;

    @Test
    void startsRecommendationComponentsWithExplicitSettings() {
        Assertions.assertThat(context.getBean(RecommendationService.class)).isNotNull();
        Assertions.assertThat(context.getBean(PlaceEmbeddingService.class)).isNotNull();
    }
}
