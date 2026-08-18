package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTests {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void exposesOnlyApiPathsWithDocumentInformationAndSecurityScheme() throws Exception {
        HttpResponse<String> response = get(uri("/api/openapi"));

        Assertions.assertThat(response.statusCode()).isEqualTo(200);

        JsonNode document = objectMapper.readTree(response.body());
        Assertions.assertThat(document.path("info").path("title").asString()).isEqualTo("가야디 API");
        Assertions.assertThat(document.path("info").path("version").asString()).isEqualTo("v1");
        Assertions.assertThat(document.path("components")
                        .path("securitySchemes")
                        .path("bearerAuth")
                        .path("scheme")
                        .asString())
                .isEqualTo("bearer");
        Assertions.assertThat(document.path("paths").properties())
                .isNotEmpty()
                .allMatch(path -> path.getKey().startsWith("/api"));

        Set<String> forbiddenSegments = Set.of(
                "signup", "login", "start", "complete", "generate", "recommend", "embed-places");
        Assertions.assertThat(document.path("paths").properties())
                .noneMatch(path -> {
                    String[] segments = path.getKey().split("/");
                    return java.util.Arrays.stream(segments).anyMatch(forbiddenSegments::contains);
                });

        document.path("paths").properties().forEach(path ->
                path.getValue().properties().forEach(operation -> {
                    if (Set.of("get", "post", "put", "patch", "delete").contains(operation.getKey())) {
                        Assertions.assertThat(operation.getValue().path("summary").asString())
                                .as(path.getKey() + " " + operation.getKey())
                                .matches(".*[가-힣].*");
                    }
                }));
    }

    @Test
    void redirectsDocsPathToSwaggerUiHtml() throws Exception {
        HttpResponse<String> redirect = get(uri("/api/docs"));

        Assertions.assertThat(redirect.statusCode()).isBetween(300, 399);
        String location = redirect.headers().firstValue("Location").orElseThrow();

        HttpResponse<String> html = get(uri("/api/docs").resolve(location));
        Assertions.assertThat(html.statusCode()).isEqualTo(200);
        Assertions.assertThat(html.body()).containsIgnoringCase("swagger-ui");
    }

    @Test
    void documentsConcreteSuccessResponsesForAndroidContracts() throws Exception {
        JsonNode document = objectMapper.readTree(get(uri("/api/openapi")).body());

        Map<OperationKey, String> objectResponses = Map.ofEntries(
                Map.entry(new OperationKey("/api/v1/auth/tokens", "post", "200"), "AuthTokenResponse"),
                Map.entry(new OperationKey("/api/v1/users/current", "get", "200"), "UserProfileResponse"),
                Map.entry(new OperationKey("/api/v1/trips", "post", "201"), "TripResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/invitations", "post", "201"), "InvitationResponse"),
                Map.entry(new OperationKey("/api/v1/trip-memberships", "post", "201"), "MembershipResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/schedules", "post", "201"), "ScheduleResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/plans", "get", "200"), "PlanResponse"),
                Map.entry(new OperationKey("/api/v1/places", "get", "200"), "PlacePageResponse"),
                Map.entry(new OperationKey("/api/v1/surveys/travel-personality-v1", "get", "200"), "SurveyResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/route-recommendations", "post", "201"), "RouteResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/dashboard", "get", "200"), "DashboardResponse"),
                Map.entry(new OperationKey("/api/v1/legal-documents/{documentId}", "get", "200"), "LegalDocumentResponse")
        );
        objectResponses.forEach((key, schemaName) ->
                Assertions.assertThat(successSchema(document, key).path("$ref").asString())
                        .as(key.path() + " " + key.method())
                        .isEqualTo("#/components/schemas/" + schemaName));

        Map<OperationKey, String> arrayResponses = Map.ofEntries(
                Map.entry(new OperationKey("/api/v1/trips", "get", "200"), "TripResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/participants", "get", "200"), "ParticipantResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/invitations", "get", "200"), "InvitationResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/schedules", "get", "200"), "ScheduleResponse"),
                Map.entry(new OperationKey("/api/v1/trips/{tripId}/route-selections", "get", "200"), "RouteResponse"),
                Map.entry(new OperationKey("/api/v1/friendships", "get", "200"), "FriendshipResponse"),
                Map.entry(new OperationKey("/api/v1/users", "get", "200"), "UserSearchResponse"),
                Map.entry(new OperationKey("/api/v1/users/current/favorite-places", "get", "200"), "FavoritePlaceResponse")
        );
        arrayResponses.forEach((key, schemaName) -> {
            JsonNode schema = successSchema(document, key);
            Assertions.assertThat(schema.path("type").asString())
                    .as(key.path() + " " + key.method())
                    .isEqualTo("array");
            Assertions.assertThat(schema.path("items").path("$ref").asString())
                    .as(key.path() + " " + key.method())
                    .isEqualTo("#/components/schemas/" + schemaName);
        });

        Map<String, Set<String>> requiredProperties = Map.ofEntries(
                Map.entry("AuthTokenResponse", Set.of("accessToken", "tokenType", "expiresIn", "user")),
                Map.entry("UserProfileResponse", Set.of("id", "email", "nickname", "characterKey")),
                Map.entry("TripResponse", Set.of("id", "name", "startDate", "endDate", "participantIds", "inviteCode")),
                Map.entry("ParticipantResponse", Set.of("userId", "participantId", "nickname", "role")),
                Map.entry("InvitationResponse", Set.of("id", "tripId", "code", "status", "expiresAt")),
                Map.entry("MembershipResponse", Set.of("trip", "participant")),
                Map.entry("ScheduleResponse", Set.of("id", "tripId", "title", "date", "time", "order", "isVisited")),
                Map.entry("PlanResponse", Set.of("id", "trip_id", "day_number", "plan_date", "days")),
                Map.entry("PlacePageResponse", Set.of("items", "nextCursor", "hasNext")),
                Map.entry("PlaceResponse", Set.of("id", "name", "category", "latitude", "longitude", "regionId")),
                Map.entry("SurveyResponse", Set.of("id", "questions", "results", "resultCodeOrder")),
                Map.entry("RouteResponse", Set.of("id", "tripId", "type", "phase", "durationMinutes", "options")),
                Map.entry("DashboardResponse", Set.of("trip", "participants", "schedules", "progress")),
                Map.entry("FriendshipResponse", Set.of("id", "user", "status", "requestedByMe")),
                Map.entry("FavoritePlaceResponse", Set.of("id", "name", "category", "memo")),
                Map.entry("LegalDocumentResponse", Set.of("id", "title", "version", "sections"))
        );
        requiredProperties.forEach((schemaName, propertyNames) -> {
            JsonNode properties = document.path("components").path("schemas")
                    .path(schemaName).path("properties");
            Assertions.assertThat(properties.isObject())
                    .as(schemaName + " 속성")
                    .isTrue();
            propertyNames.forEach(propertyName ->
                    Assertions.assertThat(properties.has(propertyName))
                            .as(schemaName + "." + propertyName)
                            .isTrue());
        });
    }

    private JsonNode successSchema(JsonNode document, OperationKey key) {
        return document.path("paths").path(key.path()).path(key.method())
                .path("responses").path(key.responseCode())
                .path("content").path("application/json").path("schema");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private record OperationKey(String path, String method, String responseCode) {
    }
}
