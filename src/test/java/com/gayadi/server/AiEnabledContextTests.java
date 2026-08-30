package com.gayadi.server;

import com.gayadi.server.recommendation.PlaceRecommendationAgent;
import com.gayadi.server.recommendation.SituationResponseAgent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "app.ai.enabled=true",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-key"
})
class AiEnabledContextTests {

    @Autowired
    ApplicationContext context;

    @Test
    void startsRecommendationComponentsWithExplicitSettings() {
        Assertions.assertThat(context.getBean(PlaceRecommendationAgent.class)).isNotNull();
        Assertions.assertThat(context.getBean(SituationResponseAgent.class)).isNotNull();
    }
}
