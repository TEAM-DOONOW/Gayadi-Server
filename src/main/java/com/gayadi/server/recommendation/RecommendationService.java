package com.gayadi.server.recommendation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' != ''")
public class RecommendationService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RecommendationService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    public PlaceRecommendationResponse recommendPlaces(PlaceRecommendationRequest request) {
        StringBuilder queryBuilder = new StringBuilder(request.getProfile());
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            queryBuilder.append(" ").append(String.join(" ", request.getKeywords()));
        }
        String query = queryBuilder.toString();

        var searchResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(request.getLimit() * 2)
                        .build()
        );

        String placeContext = searchResults.stream()
                .map(doc -> {
                    Object nameObj = doc.getMetadata().get("name");
                    Object catObj = doc.getMetadata().get("category");
                    String name = nameObj != null ? nameObj.toString() : "알 수 없음";
                    String category = catObj != null ? catObj.toString() : "기타";
                    return "- " + name + " (" + category + "): " + doc.getText();
                })
                .collect(Collectors.joining("\n"));

        String keywordsStr = (request.getKeywords() == null || request.getKeywords().isEmpty())
                ? "없음" : String.join(", ", request.getKeywords());

        return chatClient.prompt()
                .user("""
                        사용자 성향: %s
                        위치: 위도 %f, 경도 %f
                        검색 키워드: %s

                        후보 장소:
                        %s

                        위 후보 중 최대 %d개를 추천하고, 각 장소별 추천 이유를 한 문장으로 설명하세요.
                        """.formatted(request.getProfile(), request.getLatitude(),
                        request.getLongitude(), keywordsStr, placeContext, request.getLimit()))
                .call()
                .entity(PlaceRecommendationResponse.class);
    }
}
