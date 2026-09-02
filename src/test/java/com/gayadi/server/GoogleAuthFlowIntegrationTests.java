package com.gayadi.server;

import com.gayadi.server.auth.GoogleIdTokenClient;
import com.gayadi.server.auth.model.GoogleIdentity;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.auth.AuthErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.google.client-id=test-client.apps.googleusercontent.com")
@Import(GoogleAuthFlowIntegrationTests.StubGoogleConfig.class)
class GoogleAuthFlowIntegrationTests {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    ObjectMapper json;

    @Test
    void issuesServerTokenAndReusesTheSameGoogleAccount() throws Exception {
        HttpResponse<String> first = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"valid-google-id-token\"}");
        Assertions.assertThat(first.statusCode()).isEqualTo(200);
        JsonNode firstBody = json.readTree(first.body());
        Assertions.assertThat(firstBody.path("tokenType").asString()).isEqualTo("Bearer");
        Assertions.assertThat(firstBody.path("accessToken").asString()).isNotBlank();
        Assertions.assertThat(firstBody.path("user").path("email").asString())
                .isEqualTo("google-user@example.com");
        Assertions.assertThat(firstBody.path("user").path("nickname").asString())
                .isEqualTo("구글여행자");
        long userId = firstBody.path("user").path("id").asLong();

        HttpResponse<String> second = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"valid-google-id-token\"}");
        Assertions.assertThat(second.statusCode()).isEqualTo(200);
        Assertions.assertThat(json.readTree(second.body()).path("user").path("id").asLong())
                .isEqualTo(userId);
    }

    @Test
    void linksGoogleLoginToExistingEmailAccount() throws Exception {
        HttpResponse<String> signup = post("/api/v1/auth/registrations",
                "{\"email\":\"linked-google@example.com\",\"password\":\"password1\",\"nickname\":\"기존회원\"}");
        Assertions.assertThat(signup.statusCode()).isEqualTo(201);
        long existingId = json.readTree(signup.body()).path("user").path("id").asLong();

        HttpResponse<String> google = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"link-existing-email\"}");
        Assertions.assertThat(google.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(google.body());
        Assertions.assertThat(body.path("user").path("id").asLong()).isEqualTo(existingId);
        Assertions.assertThat(body.path("user").path("nickname").asString()).isEqualTo("기존회원");
    }

    @Test
    void rejectsInvalidGoogleToken() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"invalid-google-id-token\"}");
        Assertions.assertThat(response.statusCode()).isEqualTo(401);
        Assertions.assertThat(response.body()).contains("AUTH_GOOGLE_TOKEN_INVALID");
        Assertions.assertThat(response.body()).doesNotContain("invalid-google-id-token");
    }

    @Test
    void rejectsExpiredGoogleToken() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"expired-google-id-token\"}");
        Assertions.assertThat(response.statusCode()).isEqualTo(401);
        Assertions.assertThat(response.body()).contains("AUTH_GOOGLE_TOKEN_EXPIRED");
    }

    @Test
    void reusesGoogleAccountWithoutAnEmailClaim() throws Exception {
        HttpResponse<String> first = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"no-email\"}");
        Assertions.assertThat(first.statusCode()).isEqualTo(200);
        long userId = json.readTree(first.body()).path("user").path("id").asLong();

        HttpResponse<String> second = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"no-email\"}");
        Assertions.assertThat(second.statusCode()).isEqualTo(200);
        Assertions.assertThat(json.readTree(second.body()).path("user").path("id").asLong())
                .isEqualTo(userId);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class StubGoogleConfig {
        @Bean
        @Primary
        GoogleIdTokenClient stubGoogleIdTokenClient() {
            return new GoogleIdTokenClient() {
                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public GoogleIdentity verify(String idToken) {
                    return switch (idToken) {
                        case "invalid-google-id-token" ->
                                throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
                        case "expired-google-id-token" ->
                                throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_TOKEN_EXPIRED);
                        case "link-existing-email" -> new GoogleIdentity(
                                "google-sub-linked",
                                "linked-google@example.com",
                                true,
                                "구글이름",
                                null);
                        case "no-email" -> new GoogleIdentity(
                                "google-sub-no-email",
                                null,
                                false,
                                "이메일없는회원",
                                null);
                        default -> new GoogleIdentity(
                                "google-sub-default",
                                "google-user@example.com",
                                true,
                                "구글여행자",
                                "https://example.com/photo.png");
                    };
                }
            };
        }
    }
}
