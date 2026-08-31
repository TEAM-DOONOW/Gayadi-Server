package com.gayadi.server.tourapi;

import com.gayadi.server.congestion.CongestionForecastService;
import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import com.gayadi.server.tourapi.dto.response.TourListResponse;
import com.gayadi.server.tourapi.dto.response.TourPlaceResponse;
import com.gayadi.server.tourapi.dto.request.TourDiscoveryRequest;
import com.gayadi.server.tourapi.dto.response.TourDiscoveryResponse;
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
        TourPlaceResponse place = new TourPlaceResponse(
                "1", "12", "경복궁", "서울", "", "", "", "", "",
                "126.97", "37.58", "", "", "", "", "11", "110",
                "NA", "NA04", "NA0401", "", "", "", "", "");
        when(tourApi.areaBasedList(any())).thenReturn(
                new TourListResponse(List.of(place), 1, 10, null));
        when(congestion.forecastAll(any())).thenAnswer(invocation -> {
            List<CongestionForecastRequest> requests = invocation.getArgument(0);
            return requests.stream().map(request -> new CongestionForecastResponse(
                    "NORMAL", 50, "서울", request.placeName(),
                    LocalDate.parse(request.targetAt().substring(0, 10)),
                    "CALENDAR_HEURISTIC", true, false, "LOW", "예상값"))
                    .toList();
        });

        service.discover(request(LocalDate.of(2026, 9, 1)));
        TourDiscoveryResponse second =
                service.discover(request(LocalDate.of(2026, 9, 2)));

        verify(tourApi, times(1)).areaBasedList(any());
        verify(congestion, times(2)).forecastAll(any());
        assertThat(second.items()).hasSize(1);
        assertThat(second.targetDate()).isEqualTo(LocalDate.of(2026, 9, 2));
    }

    private TourDiscoveryRequest request(LocalDate date) {
        return new TourDiscoveryRequest(10, "서울", date,
                "12", null, null, null);
    }
}
