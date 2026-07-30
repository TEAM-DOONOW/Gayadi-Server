package com.gayadi.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpSmokeIntegrationTests {

    @LocalServerPort
    var port: Int = 0

    private val client = HttpClient.newHttpClient()

    @Test
    fun `serves health survey and places over HTTP`() {
        val health = get("/actuator/health")
        val survey = get("/api/v1/surveys/personality")
        val places = get("/api/v1/places")

        assertThat(health.statusCode()).isEqualTo(200)
        assertThat(health.body()).contains("\"status\":\"UP\"")
        assertThat(survey.statusCode()).isEqualTo(200)
        assertThat(survey.body()).contains("PERSONALITY")
        assertThat(places.statusCode()).isEqualTo(200)
        assertThat(places.body()).contains("서울숲", "국립중앙박물관")
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
