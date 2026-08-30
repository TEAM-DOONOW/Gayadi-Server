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
class AuthFlowIntegrationTests {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void registrationTokenAndProfileRoundTrip() throws Exception {
        HttpResponse<String> signup = post("/api/v1/auth/registrations",
                "{\"email\":\"user1@example.com\",\"password\":\"password1\",\"nickname\":\"가야디\"}");

        Assertions.assertThat(signup.statusCode()).isEqualTo(201);
        Assertions.assertThat(signup.body()).contains("\"accessToken\"");
        Assertions.assertThat(signup.body()).contains("\"tokenType\":\"Bearer\"");
        Assertions.assertThat(signup.body()).contains("\"email\":\"user1@example.com\"");
        Assertions.assertThat(signup.body()).contains("\"nickname\":\"가야디\"");

        String token = extractToken(signup.body());

        HttpResponse<String> login = post("/api/v1/auth/tokens",
                "{\"email\":\"user1@example.com\",\"password\":\"password1\"}");
        Assertions.assertThat(login.statusCode()).isEqualTo(200);
        Assertions.assertThat(login.body()).contains("\"accessToken\"");

        HttpResponse<String> me = get("/api/v1/users/current", extractToken(login.body()));
        Assertions.assertThat(me.statusCode()).isEqualTo(200);
        Assertions.assertThat(me.body()).contains("\"email\":\"user1@example.com\"");
        Assertions.assertThat(me.body()).contains("\"nickname\":\"가야디\"");

        Assertions.assertThat(token).isNotBlank();
    }

    @Test
    void rejectsWrongPasswordWith401() throws Exception {
        post("/api/v1/auth/registrations",
                "{\"email\":\"user2@example.com\",\"password\":\"password1\",\"nickname\":\"둘리\"}");
        HttpResponse<String> login = post("/api/v1/auth/tokens",
                "{\"email\":\"user2@example.com\",\"password\":\"wrong-password\"}");
        Assertions.assertThat(login.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsDuplicateEmailWith409() throws Exception {
        String body = "{\"email\":\"user3@example.com\",\"password\":\"password1\",\"nickname\":\"삼돌이\"}";
        Assertions.assertThat(post("/api/v1/auth/registrations", body).statusCode()).isEqualTo(201);
        HttpResponse<String> duplicate = post("/api/v1/auth/registrations", body);
        Assertions.assertThat(duplicate.statusCode()).isEqualTo(409);
        Assertions.assertThat(duplicate.body()).contains("이미 가입된 이메일");
    }

    @Test
    void rejectsMissingTokenWith401() throws Exception {
        HttpResponse<String> me = get("/api/v1/users/current", "");
        Assertions.assertThat(me.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsInvalidTokenWith401() throws Exception {
        HttpResponse<String> me = get("/api/v1/users/current", "Bearer invalid.token.value");
        Assertions.assertThat(me.statusCode()).isEqualTo(401);
    }

    @Test
    void correctPasswordClearsAnAttackerTriggeredTemporaryLock() throws Exception {
        post("/api/v1/auth/registrations",
                "{\"email\":\"locked@example.com\",\"password\":\"password1\",\"nickname\":\"잠금확인\"}");
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> failed = post("/api/v1/auth/tokens",
                    "{\"email\":\"locked@example.com\",\"password\":\"wrong-password\"}");
            Assertions.assertThat(failed.statusCode()).isEqualTo(401);
        }
        HttpResponse<String> lockedFailure = post("/api/v1/auth/tokens",
                "{\"email\":\"locked@example.com\",\"password\":\"wrong-password\"}");
        Assertions.assertThat(lockedFailure.statusCode()).isEqualTo(429);

        HttpResponse<String> recovered = post("/api/v1/auth/tokens",
                "{\"email\":\"locked@example.com\",\"password\":\"password1\"}");
        Assertions.assertThat(recovered.statusCode()).isEqualTo(200);
        Assertions.assertThat(recovered.body()).contains("accessToken");
    }

    private String extractToken(String body) {
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET();
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", bearer.startsWith("Bearer ") ? bearer : "Bearer " + bearer);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
