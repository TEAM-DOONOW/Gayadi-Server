package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.dto.response.PlaceRecommendationResponse;
import com.gayadi.server.recommendation.dto.response.RecommendedPlace;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 벡터 검색 결과를 기반으로 기존 추천 응답을 생성합니다. */
@Service
@ConditionalOnProperty(name = "app.ai.embedding.enabled", havingValue = "true")
public class RecommendationService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RecommendationService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    /** 여행 조건과 벡터 검색 결과를 조합해 맞춤 장소를 추천합니다. */
    /** 성향과 현재 위치를 바탕으로 공개 장소 후보를 검색하고 추천 결과를 생성합니다. */
    public PlaceRecommendationResponse recommendPlaces(PlaceRecommendationRequest request) {
        StringBuilder queryBuilder = new StringBuilder(request.getProfile());
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            queryBuilder.append(" ").append(String.join(" ", request.getKeywords()));
        }
        String query = queryBuilder.toString();

        // 먼저 성향과 키워드가 가까운 후보를 찾고 실제 좌표 거리를 기준으로 재정렬합니다.
        List<Document> searchResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(Math.min(100, request.getLimit() * 5))
                        .build()
        ).stream()
                .sorted(Comparator.comparingDouble(document -> distanceKm(
                        document, request.getLatitude(), request.getLongitude())))
                .toList();
        if (searchResults.isEmpty()) {
            return new PlaceRecommendationResponse(List.of(), "추천할 수 있는 공개 장소가 없습니다.");
        }

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

        // 언어 모델에는 검색으로 검증된 공개 장소만 전달합니다.
        PlaceRecommendationResponse generated = chatClient.prompt()
                .user("""
                        사용자 성향: %s
                        검색 키워드: %s

                        성향 유사도와 현재 위치에서의 거리를 함께 반영해 정렬한 후보 장소:
                        %s

                        위 후보 중 최대 %d개를 추천하고, 각 장소별 추천 이유를 한 문장으로 설명하세요.
                        """.formatted(request.getProfile(), keywordsStr, placeContext, request.getLimit()))
                .call()
                .entity(PlaceRecommendationResponse.class);
        if (generated == null || generated.recommendations().isEmpty()) {
            return new PlaceRecommendationResponse(List.of(), "추천할 수 있는 공개 장소가 없습니다.");
        }

        // 모델이 후보에 없던 장소를 생성하더라도 API 응답에는 포함하지 않습니다.
        Set<String> candidateIds = searchResults.stream()
                .map(document -> document.getMetadata().get("placeId"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
        List<RecommendedPlace> safeRecommendations = generated.recommendations().stream()
                .filter(place -> candidateIds.contains(place.placeId()))
                .limit(request.getLimit())
                .toList();
        return new PlaceRecommendationResponse(safeRecommendations, generated.reasoning());
    }

    private double distanceKm(Document document, double latitude, double longitude) {
        try {
            double placeLatitude = Double.parseDouble(
                    document.getMetadata().get("latitude").toString());
            double placeLongitude = Double.parseDouble(
                    document.getMetadata().get("longitude").toString());
            double latitudeDistance = Math.toRadians(placeLatitude - latitude);
            double longitudeDistance = Math.toRadians(placeLongitude - longitude);
            double value = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                    + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(placeLatitude))
                    * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
            return 6_371.0 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
        } catch (RuntimeException exception) {
            // 이전 형식의 검색 자료는 좌표가 없을 수 있으므로 뒤로 보낸다.
            return Double.MAX_VALUE;
        }
    }
}
