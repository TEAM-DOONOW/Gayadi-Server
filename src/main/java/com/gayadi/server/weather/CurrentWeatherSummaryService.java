package com.gayadi.server.weather;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.weather.dto.request.WeatherRequest;
import com.gayadi.server.weather.dto.response.PlaceWeatherSummaryResponse;
import com.gayadi.server.weather.dto.response.UltraShortNowcastResponse;
import com.gayadi.server.weather.dto.response.WeatherForecastResponse;
import com.gayadi.server.weather.dto.response.WeatherForecastSlotResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 초단기실황과 가장 가까운 초단기예보를 장소 화면용 날씨 요약으로 합칩니다. */
@Service
public class CurrentWeatherSummaryService {

    private static final Logger log = LoggerFactory.getLogger(CurrentWeatherSummaryService.class);
    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter KMA_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 기상청 호출 하나가 느려도 장소 화면 응답이 이 시간 이상 기다리지 않습니다. */
    private static final long SUMMARY_TIMEOUT_SECONDS = 6;

    private final WeatherApiService weather;
    // 공용 ForkJoinPool을 막지 않도록 기상청 블로킹 호출은 전용 작업자에서만 실행합니다.
    private final ExecutorService workers = new ThreadPoolExecutor(
            8, 8, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64),
            new ThreadPoolExecutor.AbortPolicy());

    public CurrentWeatherSummaryService(WeatherApiService weather) {
        this.weather = weather;
    }

    /** 요청 스레드가 다른 작업(혼잡 조회)과 병렬로 기다릴 수 있게 요약을 비동기로 시작합니다. */
    public CompletableFuture<PlaceWeatherSummaryResponse> summarizeAsync(double latitude, double longitude) {
        WeatherRequest request = new WeatherRequest(latitude, longitude, null, null, null, null);
        OffsetDateTime now = OffsetDateTime.now(KOREA_OFFSET);
        CompletableFuture<UltraShortNowcastResponse> observationFuture =
                submit(() -> observation(request));
        CompletableFuture<WeatherForecastSlotResponse> forecastFuture =
                submit(() -> nearestUltra(request, now));
        return observationFuture.thenCombine(forecastFuture, this::combine);
    }

    /** 좌표의 현재 기온, 하늘 상태, 강수확률을 반환합니다. 기상청 호출이 실패하면 사용 불가로 표시합니다. */
    public PlaceWeatherSummaryResponse summarize(double latitude, double longitude) {
        PlaceWeatherSummaryResponse summary = joinQuietly(summarizeAsync(latitude, longitude));
        return summary == null ? unavailable("기상청 데이터를 현재 사용할 수 없습니다.") : summary;
    }

    private PlaceWeatherSummaryResponse combine(
            UltraShortNowcastResponse observation,
            WeatherForecastSlotResponse nearestForecast) {
        if (observation == null && nearestForecast == null) {
            return unavailable("기상청 데이터를 현재 사용할 수 없습니다.");
        }

        String condition = condition(observation, nearestForecast);
        Double temperature = observation == null
                ? decimal(nearestForecast.temperature())
                : decimal(observation.temperature());
        Integer precipitationProbability = nearestForecast == null
                ? null
                : integer(nearestForecast.precipitationProbability());
        OffsetDateTime observedAt = observation == null
                ? null
                : kmaDateTime(observation.baseDate(), observation.baseTime());
        OffsetDateTime forecastAt = nearestForecast == null
                ? null
                : kmaDateTime(nearestForecast.fcstDate(), nearestForecast.fcstTime());

        return new PlaceWeatherSummaryResponse(
                true,
                condition,
                temperature,
                precipitationProbability,
                observedAt,
                forecastAt,
                "KMA",
                "기온은 초단기실황, 하늘 상태와 강수확률은 가장 가까운 초단기예보를 사용합니다.");
    }

    private UltraShortNowcastResponse observation(WeatherRequest request) {
        try {
            return weather.ultraSrtNcst(request);
        } catch (RuntimeException exception) {
            log.warn("현재 날씨 실황 보강을 생략합니다: {}", reason(exception));
            return null;
        }
    }

    private WeatherForecastSlotResponse nearestUltra(WeatherRequest request, OffsetDateTime now) {
        try {
            WeatherForecastResponse forecast = weather.ultraSrtFcst(request);
            return nearestForecast(forecast.forecast(), now).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("현재 날씨 예보 보강을 생략합니다: {}", reason(exception));
            return null;
        }
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, workers)
                    .completeOnTimeout(null, SUMMARY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException exception) {
            log.warn("현재 날씨 보강 작업이 가득 차 생략합니다.");
            return CompletableFuture.completedFuture(null);
        }
    }

    private static <T> T joinQuietly(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    private static String reason(RuntimeException exception) {
        if (exception instanceof BusinessException business) {
            return business.getErrorCode().code();
        }
        return exception.getClass().getSimpleName();
    }

    private Optional<WeatherForecastSlotResponse> nearestForecast(
            List<WeatherForecastSlotResponse> forecast,
            OffsetDateTime now) {
        if (forecast == null) {
            return Optional.empty();
        }
        return forecast.stream()
                .filter(slot -> kmaDateTime(slot.fcstDate(), slot.fcstTime()) != null)
                .min((left, right) -> Long.compare(
                        distanceSeconds(kmaDateTime(left.fcstDate(), left.fcstTime()), now),
                        distanceSeconds(kmaDateTime(right.fcstDate(), right.fcstTime()), now)));
    }

    private long distanceSeconds(OffsetDateTime value, OffsetDateTime target) {
        return Math.abs(Duration.between(value, target).toSeconds());
    }

    private String condition(
            UltraShortNowcastResponse observation,
            WeatherForecastSlotResponse forecast) {
        if (forecast != null
                && forecast.precipitationTypeName() != null
                && !forecast.precipitationTypeName().isBlank()
                && !"없음".equals(forecast.precipitationTypeName())) {
            return forecast.precipitationTypeName();
        }
        if (forecast != null && forecast.skyName() != null && !forecast.skyName().isBlank()) {
            return forecast.skyName();
        }
        if (observation != null && observation.precipitationTypeName() != null
                && !observation.precipitationTypeName().isBlank()) {
            return observation.precipitationTypeName();
        }
        return "정보 없음";
    }

    private OffsetDateTime kmaDateTime(String date, String time) {
        if (date == null || time == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(date + time, KMA_DATE_TIME).atOffset(KOREA_OFFSET);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Double decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer integer(String value) {
        try {
            return value == null || value.isBlank() ? null : (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private PlaceWeatherSummaryResponse unavailable(String message) {
        return new PlaceWeatherSummaryResponse(
                false, null, null, null, null, null, "KMA", message);
    }
}
