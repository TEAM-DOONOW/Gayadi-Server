package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.cors.allowed-origins=https://app.example.test")
class WebSecurityPolicyIntegrationTests {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void appliesSecurityHeadersToApiResponses() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(apiUri("/api/v1/places")).GET());

        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        Assertions.assertThat(response.headers().firstValue("X-Content-Type-Options"))
                .contains("nosniff");
        Assertions.assertThat(response.headers().firstValue("X-Frame-Options"))
                .contains("DENY");
        Assertions.assertThat(response.headers().firstValue("Referrer-Policy"))
                .contains("no-referrer");
        Assertions.assertThat(response.headers().firstValue("Permissions-Policy"))
                .contains("camera=(), microphone=(), geolocation=(), payment=()");
        Assertions.assertThat(response.headers().firstValue("Content-Security-Policy-Report-Only"))
                .hasValueSatisfying(policy -> Assertions.assertThat(policy)
                        .contains("default-src 'self'"));
    }

    @Test
    void allowsOnlyConfiguredCorsOrigin() throws Exception {
        HttpResponse<String> allowed = preflight("https://app.example.test");
        HttpResponse<String> denied = preflight("https://attacker.example.test");

        Assertions.assertThat(allowed.statusCode()).isEqualTo(200);
        Assertions.assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://app.example.test");
        Assertions.assertThat(allowed.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(methods -> Assertions.assertThat(methods).contains("POST"));

        Assertions.assertThat(denied.statusCode()).isEqualTo(403);
        Assertions.assertThat(denied.headers().firstValue("Access-Control-Allow-Origin"))
                .isEmpty();
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(apiUri("/api/v1/auth/tokens"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody());
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI apiUri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
