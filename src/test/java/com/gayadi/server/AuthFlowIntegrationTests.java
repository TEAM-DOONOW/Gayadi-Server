package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import com.gayadi.server.auth.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTests {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    JwtService jwtService;

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
    void rejectsGoogleLoginWhenClientIdIsMissing() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/google-tokens",
                "{\"idToken\":\"aaa.bbb.ccc\"}");
        Assertions.assertThat(response.statusCode()).isEqualTo(503);
        Assertions.assertThat(response.body()).contains("AUTH_GOOGLE_NOT_CONFIGURED");
        Assertions.assertThat(response.body()).doesNotContain("aaa.bbb.ccc");
    }

    @Test
    void doesNotCreateDefaultInMemoryUserCredentials() {
        Assertions.assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void rejectsMissingTokenWith401() throws Exception {
        HttpResponse<String> me = get("/api/v1/users/current", "");
        assertSecurityError(me, "UNAUTHENTICATED", "/api/v1/users/current");
    }

    @Test
    void rejectsInvalidTokenWith401() throws Exception {
        HttpResponse<String> me = get("/api/v1/users/current", "Bearer invalid.token.value");
        assertSecurityError(me, "AUTH_TOKEN_INVALID", "/api/v1/users/current");
        Assertions.assertThat(me.body()).doesNotContain("invalid.token.value");
    }

    @Test
    void rejectsExpiredTokenWithDedicatedErrorCode() throws Exception {
        HttpResponse<String> signup = post("/api/v1/auth/registrations",
                "{\"email\":\"expired-token@example.com\",\"password\":\"password1\",\"nickname\":\"만료확인\"}");
        Assertions.assertThat(signup.statusCode()).isEqualTo(201);
        long userId = jwtService.parseAndGetUserId(extractToken(signup.body()));
        Instant now = Instant.now();
        String expired = jwtService.issue(userId, now.minusSeconds(120), now.minusSeconds(5));

        HttpResponse<String> me = get("/api/v1/users/current", expired);
        assertSecurityError(me, "AUTH_TOKEN_EXPIRED", "/api/v1/users/current");
        Assertions.assertThat(me.statusCode()).isEqualTo(401);
    }

    @Test
    void withdrawsAccountAndRejectsThePreviousToken() throws Exception {
        HttpResponse<String> signup = post("/api/v1/auth/registrations",
                "{\"email\":\"withdraw@example.com\",\"password\":\"password1\",\"nickname\":\"탈퇴확인\"}");
        Assertions.assertThat(signup.statusCode()).isEqualTo(201);
        String token = extractToken(signup.body());

        HttpResponse<String> profile = get("/api/v1/users/current", token);
        Assertions.assertThat(profile.statusCode()).isEqualTo(200);

        HttpResponse<String> withdrawn = request("DELETE", "/api/v1/users/current", token, null);
        Assertions.assertThat(withdrawn.statusCode()).isEqualTo(204);

        HttpResponse<String> me = get("/api/v1/users/current", token);
        assertSecurityError(me, "AUTH_ACCOUNT_UNAVAILABLE", "/api/v1/users/current");
        Assertions.assertThat(me.statusCode()).isEqualTo(403);

        HttpResponse<String> login = post("/api/v1/auth/tokens",
                "{\"email\":\"withdraw@example.com\",\"password\":\"password1\"}");
        Assertions.assertThat(login.statusCode()).isEqualTo(401);
    }

    @Test
    void protectedNounApisRequireBearerTokenAndPublicNounApisStayOpen() throws Exception {
        Assertions.assertThat(get("/api/v1/trips", "").statusCode()).isEqualTo(401);
        Assertions.assertThat(get("/api/v1/congestion/forecast?areaCode=11&districtCode=110", "")
                .statusCode()).isEqualTo(401);
        Assertions.assertThat(get("/api/v1/weather/nowcasts?nx=60&ny=127", "").statusCode())
                .isEqualTo(401);
        Assertions.assertThat(get("/api/v1/tour/locations?mapX=126.98&mapY=37.56&radius=1000", "")
                .statusCode()).isEqualTo(401);
        Assertions.assertThat(get("/api/v1/tour/keywords?keyword=시장", "").statusCode()).isEqualTo(401);
        Assertions.assertThat(get("/api/v1/tour/stays", "").statusCode()).isEqualTo(401);
        Assertions.assertThat(get("/api/v1/tour/festivals?eventStartDate=20260101", "").statusCode())
                .isEqualTo(401);
        Assertions.assertThat(request("POST", "/api/v1/recommendations/places", null, "{}")
                .statusCode()).isEqualTo(401);

        Assertions.assertThat(get("/api/v1/places", "").statusCode()).isEqualTo(200);
        Assertions.assertThat(get("/api/v1/notices", "").statusCode()).isEqualTo(200);
        Assertions.assertThat(get("/api/v1/surveys/travel-personality-v1", "").statusCode()).isEqualTo(200);
        Assertions.assertThat(get("/api/v1/legal-documents/terms-of-service", "").statusCode())
                .isEqualTo(200);
        Assertions.assertThat(get("/api/v1/tour/areas", "").statusCode()).isEqualTo(400);

        HttpResponse<String> signup = post("/api/v1/auth/registrations",
                "{\"email\":\"noun-api@example.com\",\"password\":\"password1\",\"nickname\":\"명사확인\"}");
        String token = extractToken(signup.body());
        Assertions.assertThat(get("/api/v1/tour/discover?regionName=%EC%84%9C%EC%9A%B8", token)
                .statusCode()).isEqualTo(404);
        Assertions.assertThat(request("POST", "/api/v1/recommendations/situations", token, "{}")
                .statusCode()).isEqualTo(404);
        Assertions.assertThat(get("/api/v1/weather/now?nx=60&ny=127", token).statusCode())
                .isEqualTo(404);
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

    private void assertSecurityError(HttpResponse<String> response, String code, String path) {
        Assertions.assertThat(response.statusCode()).isIn(401, 403);
        Assertions.assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        Assertions.assertThat(response.body())
                .contains("\"status\":" + response.statusCode())
                .contains("\"code\":\"" + code + "\"")
                .contains("\"path\":\"" + path + "\"")
                .contains("\"traceId\":")
                .contains("\"details\":null")
                .doesNotContain("stackTrace")
                .doesNotContain("exception");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return request("POST", path, null, body);
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return request("GET", path, bearer, null);
    }
}
