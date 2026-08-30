package com.gayadi.server.tourapi;

import com.gayadi.server.congestion.CongestionForecast;
import com.gayadi.server.congestion.CongestionForecastService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourDiscoveryServiceTest {

    @Test
    void reusesPlaceResultsWhenOnlyTheTravelDateChanges() {
        TourApiService tourApi = mock(TourApiService.class);
        CongestionForecastService congestion = mock(CongestionForecastService.class);
        TourRegionResolver resolver = new TourRegionResolver(tourApi);
        TourDiscoveryService service = new TourDiscoveryService(tourApi, resolver, congestion);
        TourApiService.TourPlace place = new TourApiService.TourPlace(
                "1", "12", "경복궁", "서울", "", "", "", "", "",
                "126.97", "37.58", "", "", "", "", "11", "110",
                "NA", "NA04", "NA0401", "", "", "", "", "");
        when(tourApi.areaBasedList(any())).thenReturn(
                new TourApiService.TourListResponse(List.of(place), 1, 10, null));
        when(congestion.forecastAll(any())).thenAnswer(invocation -> {
            List<CongestionForecastService.Request> requests = invocation.getArgument(0);
            return requests.stream().map(request -> new CongestionForecast(
                    "NORMAL", 50, "서울", request.placeName(),
                    LocalDate.parse(request.targetAt().substring(0, 10)),
                    "CALENDAR_HEURISTIC", true, false, "LOW", "예상값"))
                    .toList();
        });

        service.discover(request(LocalDate.of(2026, 9, 1)));
        TourDiscoveryService.DiscoveryResponse second =
                service.discover(request(LocalDate.of(2026, 9, 2)));

        verify(tourApi, times(1)).areaBasedList(any());
        verify(congestion, times(2)).forecastAll(any());
        assertThat(second.items()).hasSize(1);
        assertThat(second.targetDate()).isEqualTo(LocalDate.of(2026, 9, 2));
    }

    private TourDiscoveryService.Request request(LocalDate date) {
        return new TourDiscoveryService.Request(10, "서울", date,
                "12", null, null, null);
    }
}
