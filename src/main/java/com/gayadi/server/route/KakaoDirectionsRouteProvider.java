package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import com.gayadi.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Kakao 자동차 길찾기의 최단 시간 경로를 계산합니다. 대중교통으로 대체하지 않습니다. */
@Component
public class KakaoDirectionsRouteProvider implements RouteProvider {
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public KakaoDirectionsRouteProvider(
            ObjectMapper mapper,
            @Value("${route.kakao.api-key:${KAKAO_DIRECTIONS_API_KEY:}}") String apiKey,
            @Value("${route.kakao.base-url:https://apis-navi.kakaomobility.com/v1/directions}") String baseUrl) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public String providerName() {
        return "KAKAO_DIRECTIONS";
    }

    @Override
    public TransportMode transportMode() {
        return TransportMode.CAR;
    }

    @Override
    public List<RouteEstimate> estimateSegments(List<Location> stops, String phase) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(RouteErrorCode.KAKAO_NOT_CONFIGURED);
        }
        List<RouteEstimate> result = new ArrayList<>();
        for (int i = 1; i < stops.size(); i++) {
            result.add(estimate(stops.get(i - 1), stops.get(i)));
        }
        return List.copyOf(result);
    }

    private RouteEstimate estimate(Location from, Location to) {
        if (from.latitude() == to.latitude() && from.longitude() == to.longitude()) {
            return new RouteEstimate(0, 0, 0, "동일 위치", providerName());
        }
        HttpResponse<String> response;
        try {
            URI uri = URI.create(baseUrl + "?origin=" + from.longitude() + "," + from.latitude()
                    + "&destination=" + to.longitude() + "," + to.latitude()
                    + "&priority=TIME&summary=true&alternatives=false");
            response = client.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "KakaoAK " + apiKey)
                    .header("Accept", "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        }
        if (response.statusCode() == 429) {
            throw new BusinessException(RouteErrorCode.KAKAO_RATE_LIMITED);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        }
        try {
            JsonNode routes = mapper.readTree(response.body()).path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
            }
            JsonNode route = routes.get(0);
            if (route.path("result_code").asInt(-1) != 0) {
                throw new BusinessException(RouteErrorCode.KAKAO_ROUTE_UNAVAILABLE);
            }
            JsonNode summary = route.path("summary");
            double seconds = summary.path("duration").asDouble(-1);
            int toll = summary.path("fare").path("toll").asInt(-1);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > Integer.MAX_VALUE || toll < 0) {
                throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
            }
            return new RouteEstimate((int) Math.ceil(seconds / 60), 0, toll,
                    "Kakao 자동차 예상 이동시간 · 비용은 통행료이며 유류비·주차비 제외", providerName());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        }
    }
}
