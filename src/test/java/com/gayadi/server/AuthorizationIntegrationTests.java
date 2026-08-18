package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationIntegrationTests {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void blocksAnotherUsersTripAndIgnoresSpoofedOwnerFields() throws Exception {
        String ownerToken = register("owner-auth@example.com", "여행장");
        String outsiderToken = register("outsider-auth@example.com", "외부인");

        HttpResponse<String> created = request(
                "POST", "/api/v1/trips", ownerToken,
                """
                {"name":"권한 확인 여행","startDate":"2026.08.20","endDate":"2026.08.21",
                 "cities":["서울"],"ownerId":999999}
                """);
        Assertions.assertThat(created.statusCode()).isEqualTo(201);
        Assertions.assertThat(created.body()).contains(
                "\"startDate\":\"2026.08.20\"",
                "\"endDate\":\"2026.08.21\"");
        long tripId = longField(created.body(), "id");
        long ownerId = longField(created.body(), "ownerId");
        Assertions.assertThat(ownerId).isNotEqualTo(999999L);

        HttpResponse<String> schedule = request(
                "POST", "/api/v1/trips/" + tripId + "/schedules", ownerToken,
                """
                {"title":"점 표기 일정","date":"2026.08.20","time":"09:30","type":"MAIN"}
                """);
        Assertions.assertThat(schedule.statusCode()).isEqualTo(201);
        Assertions.assertThat(schedule.body()).contains(
                "\"date\":\"2026.08.20\"", "\"time\":\"09:30\"");

        HttpResponse<String> impossibleDate = request(
                "POST", "/api/v1/trips", ownerToken,
                """
                {"name":"잘못된 날짜","startDate":"2026.02.30","endDate":"2026.03.01",
                 "cities":["서울"]}
                """);
        Assertions.assertThat(impossibleDate.statusCode()).isEqualTo(400);

        HttpResponse<String> outsiderRead = request(
                "GET", "/api/v1/trips/" + tripId, outsiderToken, null);
        HttpResponse<String> outsiderStatus = request(
                "PATCH", "/api/v1/trips/" + tripId + "/status", outsiderToken,
                "{\"status\":\"ONGOING\"}");

        Assertions.assertThat(outsiderRead.statusCode()).isEqualTo(403);
        Assertions.assertThat(outsiderStatus.statusCode()).isEqualTo(403);

        HttpResponse<String> deleted = request(
                "DELETE", "/api/v1/trips/" + tripId, ownerToken, null);
        Assertions.assertThat(deleted.statusCode()).isEqualTo(204);
        HttpResponse<String> deletedSchedules = request(
                "GET", "/api/v1/trips/" + tripId + "/schedules", ownerToken, null);
        Assertions.assertThat(deletedSchedules.statusCode()).isEqualTo(403);
    }

    private String register(String email, String nickname) throws Exception {
        HttpResponse<String> response = request(
                "POST", "/api/v1/auth/registrations", null,
                "{\"email\":\"" + email + "\",\"password\":\"password1\",\"nickname\":\""
                        + nickname + "\"}");
        Assertions.assertThat(response.statusCode()).isEqualTo(201);
        return stringField(response.body(), "accessToken");
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String stringField(String json, String field) {
        String prefix = "\"" + field + "\":\"";
        int start = json.indexOf(prefix) + prefix.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private long longField(String json, String field) {
        String prefix = "\"" + field + "\":";
        int start = json.indexOf(prefix) + prefix.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Long.parseLong(json.substring(start, end));
    }
}
