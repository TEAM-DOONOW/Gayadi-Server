package com.gayadi.server.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("spring.ai.openai.api-key")
class AiConfig {

    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(
                """
                당신은 GAYADI 여행 추천 엔진입니다.
                사용자 성향, 날씨, 위치 정보를 바탕으로 최적의 여행 장소와 일정을 추천합니다.
                항상 한국어로 응답합니다.
                """.trimIndent()
            )
            .build()

    @Bean
    @ConditionalOnMissingBean(VectorStore::class)
    fun vectorStore(embeddingModel: EmbeddingModel): VectorStore =
        SimpleVectorStore.builder(embeddingModel).build()
}
