package com.gayadi.server.recommendation;

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

@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
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
