package com.gayadi.server.recommendation

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(ChatClient::class)
class RecommendationService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore
) {

    fun recommendPlaces(request: RecommendationController.PlaceRecommendationRequest): PlaceRecommendationResponse {
        val query = buildString {
            append(request.profile)
            if (request.keywords.isNotEmpty()) {
                append(" ")
                append(request.keywords.joinToString(" "))
            }
        }

        val searchResults = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(request.limit * 2)
                .build()
        )

        val placeContext = searchResults.joinToString("\n") { doc ->
            val name = doc.metadata["name"] ?: "알 수 없음"
            val category = doc.metadata["category"] ?: "기타"
            "- $name ($category): ${doc.text}"
        }

        return chatClient.prompt()
            .user(
                """
                사용자 성향: ${request.profile}
                위치: 위도 ${request.latitude}, 경도 ${request.longitude}
                검색 키워드: ${request.keywords.joinToString(", ").ifEmpty { "없음" }}

                후보 장소:
                $placeContext

                위 후보 중 최대 ${request.limit}개를 추천하고, 각 장소별 추천 이유를 한 문장으로 설명하세요.
                """.trimIndent()
            )
            .call()
            .entity(PlaceRecommendationResponse::class.java)
    }
}

data class PlaceRecommendationResponse(
    val recommendations: List<RecommendedPlace> = emptyList(),
    val reasoning: String = ""
)

data class RecommendedPlace(
    val placeId: String = "",
    val name: String = "",
    val category: String = "",
    val score: Double = 0.0,
    val reason: String = ""
)
