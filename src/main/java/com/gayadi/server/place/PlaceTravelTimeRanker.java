package com.gayadi.server.place;

import com.gayadi.server.common.Location;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.place.dto.response.PlaceResponse;
import com.gayadi.server.place.dto.response.PlaceTravelTimeResponse;
import com.gayadi.server.route.RouteErrorCode;
import com.gayadi.server.route.RouteProvider;
import com.gayadi.server.route.TransportMode;
import com.gayadi.server.route.TransitRoutingOptions;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 장소찾기 후보만 계산하며 일정 생성·수정이나 경로 추천 저장은 수행하지 않습니다. */
@Service
public class PlaceTravelTimeRanker {
    private final List<RouteProvider> providers;
    private final ExecutorService workers = new ThreadPoolExecutor(
            4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(80),
            new ThreadPoolExecutor.AbortPolicy());

    public PlaceTravelTimeRanker(List<RouteProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<PlaceResponse> rank(
            List<PlaceResponse> candidates, Location origin, Location next, TransportMode mode,
            TransitRoutingOptions options) {
        if (candidates.isEmpty()) return List.of();
        RouteProvider provider = providers.stream().filter(value -> value.transportMode() == mode)
                .findFirst().orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED));
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        List<Future<PlaceResponse>> tasks = new ArrayList<>();
        try {
            // 다음 장소가 있으면 모든 후보에 공통인 직행 구간은 한 번만 조회합니다.
            RouteProvider.RouteEstimate baseline = next == null ? null
                    : estimates(provider, List.of(origin, next), options).getFirst();
            for (PlaceResponse candidate : candidates) {
                tasks.add(workers.submit(() -> evaluate(candidate, origin, next, mode, provider, baseline, options)));
            }
            List<PlaceResponse> ranked = new ArrayList<>();
            for (Future<PlaceResponse> task : tasks) {
                PlaceResponse result = task.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (result != null) ranked.add(result);
            }
            ranked.sort(Comparator.comparingLong((PlaceResponse value) -> next == null
                            ? value.travelTime().durationMinutes() : value.travelTime().additionalDurationMinutes())
                    .thenComparingLong(PlaceResponse::id));
            return List.copyOf(ranked);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof BusinessException business) throw business;
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        } catch (TimeoutException | RejectedExecutionException exception) {
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        } finally {
            tasks.forEach(task -> task.cancel(true));
        }
    }

    private PlaceResponse evaluate(PlaceResponse candidate, Location origin, Location next,
                                   TransportMode mode, RouteProvider provider, RouteProvider.RouteEstimate baseline,
                                   TransitRoutingOptions options) {
        Location location = new Location(candidate.name(), candidate.latitude(), candidate.longitude());
        List<RouteProvider.RouteEstimate> legs;
        try {
            legs = estimates(provider, next == null ? List.of(origin, location) : List.of(origin, location, next), options);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == RouteErrorCode.KAKAO_ROUTE_UNAVAILABLE
                    || exception.getErrorCode() == RouteErrorCode.TMAP_ROUTE_UNAVAILABLE) return null;
            throw exception;
        }
        int incoming = legs.getFirst().durationMinutes();
        Integer outgoing = next == null ? null : legs.getLast().durationMinutes();
        Long additional = next == null ? null : (long) incoming + outgoing - baseline.durationMinutes();
        boolean fallback = legs.stream().anyMatch(leg -> isFallback(leg, provider))
                || baseline != null && isFallback(baseline, provider);
        return candidate.withTravelTime(new PlaceTravelTimeResponse(mode, incoming, outgoing,
                additional, provider.providerName(), fallback,
                options.departureAt(), next == null ? null : options.departureAt()
                        .plusMinutes((long) incoming + options.stopoverMinutes()),
                legs.stream().mapToInt(RouteProvider.RouteEstimate::transferCount).sum(),
                mode == TransportMode.PUBLIC_TRANSIT ? options.preference() : null,
                provider.supportsScheduledDeparture() && !fallback));
    }

    private boolean isFallback(RouteProvider.RouteEstimate estimate, RouteProvider provider) {
        return estimate.providerName() != null && !estimate.providerName().isBlank()
                && !provider.providerName().equals(estimate.providerName());
    }

    private List<RouteProvider.RouteEstimate> estimates(
            RouteProvider provider, List<Location> stops, TransitRoutingOptions options) {
        List<RouteProvider.RouteEstimate> result = provider.supportsScheduledDeparture()
                ? provider.estimateSegments(stops, "IN_TRIP", options)
                : provider.estimateSegments(stops, "IN_TRIP");
        if (result == null || result.size() != stops.size() - 1
                || result.stream().anyMatch(leg -> leg == null || leg.durationMinutes() < 0 || leg.transferCount() < 0)) {
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        }
        return result;
    }

    @PreDestroy
    public void close() {
        workers.shutdownNow();
    }
}
