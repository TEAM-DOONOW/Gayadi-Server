package com.gayadi.server.route;

import com.gayadi.server.place.PlaceRepository;
import com.gayadi.server.place.model.PlaceCategory;
import com.gayadi.server.place.query.PlaceQueryResult;
import com.gayadi.server.route.dto.request.ItineraryRecommendationRequest;
import com.gayadi.server.route.dto.response.ItineraryStopResponse;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.tourapi.TourDiscoveryService;
import com.gayadi.server.tourapi.TourApiErrorCode;
import com.gayadi.server.tourapi.dto.request.TourDiscoveryRequest;
import com.gayadi.server.travel.TripRepository;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.model.TripStatus;
import com.gayadi.server.travel.query.TripQueryResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryRecommendationServiceTest {

    private final ItineraryRecommendationService service =
            new ItineraryRecommendationService(null, null, null, null, null);

    @Test
    void buildsTimedRouteWithinRequestedDay() {
        var response = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.PUBLIC_TRANSIT, 0, List.of()),
                candidates(),
                LocalTime.of(10, 0),
                LocalTime.of(18, 0));

        assertThat(response.stops()).hasSizeBetween(2, 6);
        assertThat(response.stops()).extracting(stop -> stop.order())
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, response.stops().size()).boxed().toList());
        assertThat(response.stops().getFirst().arrivalTime()).isEqualTo("10:00");
        assertThat(LocalTime.parse(response.stops().getLast().departureTime()))
                .isBeforeOrEqualTo(LocalTime.of(18, 0));
        assertThat(response.totalStayMinutes()).isPositive();
        assertThat(response.totalTravelMinutes()).isPositive();
    }

    @Test
    void variationProducesAnotherWholeRoute() {
        var first = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.WALK, 0, List.of()),
                candidates(), LocalTime.of(10, 0), LocalTime.of(18, 0));
        var next = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.WALK, 1, List.of()),
                candidates(), LocalTime.of(10, 0), LocalTime.of(18, 0));

        assertThat(next.stops().getFirst().placeId()).isNotEqualTo(first.stops().getFirst().placeId());
        assertThat(next.variation()).isEqualTo(1);
    }

    @Test
    void variationChangesRouteWhenOnlyThreeCandidatesExist() {
        List<PlaceQueryResult> threeCandidates = candidates().subList(0, 3);
        var first = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.PUBLIC_TRANSIT, 0, List.of()),
                threeCandidates, LocalTime.of(10, 0), LocalTime.of(18, 0));
        var next = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.PUBLIC_TRANSIT, 1, List.of()),
                threeCandidates, LocalTime.of(10, 0), LocalTime.of(18, 0));

        assertThat(next.stops()).extracting(ItineraryStopResponse::placeId)
                .isNotEqualTo(first.stops().stream().map(ItineraryStopResponse::placeId).toList());
    }

    @Test
    void choosesTheCloserPlaceEvenWhenADistantPlaceIsListedFirst() {
        List<PlaceQueryResult> places = List.of(
                place(1, "화성행궁", PlaceCategory.ATTRACTION, 37.2636, 127.0286),
                place(2, "부산 감천", PlaceCategory.ATTRACTION, 35.0976, 129.0106),
                place(3, "행리단길", PlaceCategory.CAFE, 37.2670, 127.0300));

        var response = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.PUBLIC_TRANSIT, 0, List.of()),
                places, LocalTime.of(10, 0), LocalTime.of(18, 0));

        assertThat(response.stops()).extracting(ItineraryStopResponse::placeId)
                .startsWith(1L, 3L);
        assertThat(response.stops().get(1).distanceMetersFromPrevious()).isLessThan(2_000);
        assertThat(response.stops().get(1).travelMinutesFromPrevious()).isLessThan(30);
    }

    @Test
    void skipsANearbyPlaceThatDoesNotFitAndKeepsAFartherPlaceThatDoes() {
        List<PlaceQueryResult> places = new java.util.ArrayList<>();
        places.add(place(1, "수원 카페", PlaceCategory.CAFE, 37.2630, 127.0280));
        for (int index = 0; index < 5; index++) {
            places.add(place(10 + index, "가까운 관광지 " + index, PlaceCategory.ATTRACTION,
                    37.2632 + index * 0.0002, 127.0280));
        }
        places.add(place(2, "조금 먼 카페", PlaceCategory.CAFE, 37.2765, 127.0280));

        var response = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "12:05", TransportMode.WALK, 0, List.of()),
                places, LocalTime.of(10, 0), LocalTime.of(12, 5));

        assertThat(response.stops()).extracting(ItineraryStopResponse::name)
                .containsExactly("수원 카페", "조금 먼 카페");
        assertThat(response.stops().get(1).distanceMetersFromPrevious()).isBetween(1_400, 1_600);
        assertThat(response.stops().get(1).travelMinutesFromPrevious()).isEqualTo(22);
    }

    @Test
    void loadsTourPlacesWhenTheSavedRegionHasNoRouteCandidates() {
        TripService trips = mock(TripService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        PlaceRepository places = mock(PlaceRepository.class);
        ScheduleItemService schedules = mock(ScheduleItemService.class);
        TourDiscoveryService discovery = mock(TourDiscoveryService.class);
        ItineraryRecommendationService regional = new ItineraryRecommendationService(
                trips, tripRepository, places, schedules, discovery);
        when(tripRepository.find(8L)).thenReturn(Optional.of(new TripQueryResult(
                8L, 3L, "수원 여행", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                DepartureMode.TOGETHER, null, null, 1L, null,
                TripStatus.PLANNING, null, null, null, null, null, 0, "invite")));
        when(tripRepository.findCities(8L)).thenReturn(List.of("수원·용인"));
        PlaceQueryResult palace = place(21, "화성행궁", PlaceCategory.ATTRACTION, 37.2636, 127.0286);
        PlaceQueryResult cafe = place(22, "행리단길", PlaceCategory.CAFE, 37.2670, 127.0300);
        when(places.findItineraryCandidates(eq("수원·용인"), anyInt()))
                .thenReturn(List.of(), List.of(palace, cafe));

        var response = regional.recommend(3L, 8L, new ItineraryRecommendationRequest(
                "2026.10.03", "10:00", "18:00", TransportMode.WALK, 0, List.of()));

        verify(discovery).discover(new TourDiscoveryRequest(
                20, "수원·용인", null, null, null, null, null));
        assertThat(response.stops()).extracting(ItineraryStopResponse::placeId)
                .containsExactly(21L, 22L);
        assertThat(response.stops().get(1).distanceMetersFromPrevious()).isPositive();
    }

    @Test
    void doesNotCallTourApiWhenSavedPlacesAlreadyCoverTheRegion() {
        TripService trips = mock(TripService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        PlaceRepository places = mock(PlaceRepository.class);
        TourDiscoveryService discovery = mock(TourDiscoveryService.class);
        ItineraryRecommendationService regional = new ItineraryRecommendationService(
                trips, tripRepository, places, mock(ScheduleItemService.class), discovery);
        when(tripRepository.find(8L)).thenReturn(Optional.of(new TripQueryResult(
                8L, 3L, "부산 여행", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                DepartureMode.TOGETHER, null, null, 1L, null,
                TripStatus.PLANNING, null, null, null, null, null, 0, "invite")));
        when(tripRepository.findCities(8L)).thenReturn(List.of("부산"));
        List<PlaceQueryResult> busan = new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++) {
            busan.add(place(31 + index, "부산 장소 " + index, PlaceCategory.ATTRACTION,
                    35.0976 + index * 0.002, 129.0106));
        }
        when(places.findItineraryCandidates(eq("부산"), anyInt())).thenReturn(busan);

        regional.recommend(3L, 8L, new ItineraryRecommendationRequest(
                "2026.10.03", "10:00", "18:00", TransportMode.PUBLIC_TRANSIT, 0, List.of()));

        verify(discovery, never()).discover(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsUsingSavedPlacesWhenOptionalTourEnrichmentIsUnavailable() {
        TripService trips = mock(TripService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        PlaceRepository places = mock(PlaceRepository.class);
        TourDiscoveryService discovery = mock(TourDiscoveryService.class);
        ItineraryRecommendationService regional = new ItineraryRecommendationService(
                trips, tripRepository, places, mock(ScheduleItemService.class), discovery);
        when(tripRepository.find(8L)).thenReturn(Optional.of(trip()));
        when(tripRepository.findCities(8L)).thenReturn(List.of("서울"));
        when(places.findItineraryCandidates(eq("서울"), anyInt()))
                .thenReturn(candidates().subList(0, 4));
        when(discovery.discover(any())).thenThrow(
                new BusinessException(TourApiErrorCode.TOUR_API_NOT_CONFIGURED));

        var response = regional.recommend(3L, 8L, new ItineraryRecommendationRequest(
                "2026.10.03", "10:00", "18:00", TransportMode.PUBLIC_TRANSIT, 0, List.of()));

        assertThat(response.stops()).hasSizeBetween(2, 4);
        verify(discovery).discover(new TourDiscoveryRequest(
                20, "서울", null, null, null, null, null));
    }

    @Test
    void neverSchedulesAStopPastMidnight() {
        var response = service.build(
                new ItineraryRecommendationRequest(
                        "2026-10-03", "12:00", "23:59", TransportMode.PUBLIC_TRANSIT, 0, List.of()),
                candidates(), LocalTime.of(12, 0), LocalTime.of(23, 59));

        assertThat(response.date()).isEqualTo("2026.10.03");
        for (ItineraryStopResponse stop : response.stops()) {
            assertThat(LocalTime.parse(stop.arrivalTime())).isAfterOrEqualTo(LocalTime.of(12, 0));
            assertThat(LocalTime.parse(stop.departureTime()))
                    .isAfter(LocalTime.parse(stop.arrivalTime()))
                    .isBeforeOrEqualTo(LocalTime.of(23, 59));
        }
    }

    @Test
    void requiresATripRegionInsteadOfUsingNationwidePlaces() {
        TripRepository tripRepository = mock(TripRepository.class);
        PlaceRepository places = mock(PlaceRepository.class);
        ItineraryRecommendationService regional = new ItineraryRecommendationService(
                mock(TripService.class), tripRepository, places, mock(ScheduleItemService.class), null);
        when(tripRepository.find(8L)).thenReturn(Optional.of(trip()));
        when(tripRepository.findCities(8L)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> regional.recommend(3L, 8L,
                        new ItineraryRecommendationRequest(
                                "2026.10.03", "10:00", "18:00", TransportMode.WALK, 0, List.of())))
                .isInstanceOfSatisfying(com.gayadi.server.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RouteErrorCode.ROUTE_REGION_REQUIRED));
        verify(places, never()).findItineraryCandidates(org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void appliesOnlyTheRoutePreviewedByTheUser() {
        TripRepository tripRepository = mock(TripRepository.class);
        PlaceRepository places = mock(PlaceRepository.class);
        ScheduleItemService schedules = mock(ScheduleItemService.class);
        ItineraryRecommendationService regional = new ItineraryRecommendationService(
                mock(TripService.class), tripRepository, places, schedules, null);
        when(tripRepository.find(8L)).thenReturn(Optional.of(trip()));
        when(tripRepository.findCities(8L)).thenReturn(List.of("서울"));
        when(places.findItineraryCandidates(eq("서울"), anyInt())).thenReturn(candidates());
        var preview = regional.recommend(3L, 8L, new ItineraryRecommendationRequest(
                "2026.10.03", "10:00", "18:00", TransportMode.WALK, 0, List.of()));
        List<Long> previewIds = preview.stops().stream().map(ItineraryStopResponse::placeId).toList();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> regional.apply(3L, 8L, "2026-10-03",
                        new com.gayadi.server.route.dto.request.ItinerarySelectionRequest(
                                "10:00", "18:00", TransportMode.WALK, 0, List.of(999L))))
                .isInstanceOfSatisfying(com.gayadi.server.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RouteErrorCode.ROUTE_CALCULATION_CHANGED));
        verify(schedules, never()).replaceMainRoute(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        var applied = regional.apply(3L, 8L, "2026-10-03",
                new com.gayadi.server.route.dto.request.ItinerarySelectionRequest(
                        "10:00", "18:00", TransportMode.WALK, 0, previewIds));
        assertThat(applied.stops()).extracting(ItineraryStopResponse::placeId).isEqualTo(previewIds);
        verify(schedules).replaceMainRoute(eq(3L), eq(8L), eq(LocalDate.of(2026, 10, 3)),
                org.mockito.ArgumentMatchers.argThat(stops -> stops.size() == previewIds.size()));
    }

    @Test
    void placesRestaurantsAtMealTimeAndStartsWithSightseeing() {
        List<PlaceQueryResult> places = List.of(
                place(1, "아침 식당", PlaceCategory.RESTAURANT, 37.5700, 126.9770),
                place(2, "경복궁", PlaceCategory.ATTRACTION, 37.5796, 126.9770),
                place(3, "점심 식당", PlaceCategory.RESTAURANT, 37.5760, 126.9780),
                place(4, "북촌", PlaceCategory.CULTURE, 37.5826, 126.9830),
                place(5, "삼청동 카페", PlaceCategory.CAFE, 37.5845, 126.9810),
                place(6, "인사동", PlaceCategory.SHOPPING, 37.5740, 126.9850));

        var response = service.build(
                new ItineraryRecommendationRequest(
                        "2026.10.03", "10:00", "18:00", TransportMode.WALK, 0, List.of()),
                places, LocalTime.of(10, 0), LocalTime.of(18, 0));

        assertThat(response.stops().getFirst().category()).isNotEqualTo("맛집");
        assertThat(response.stops()).filteredOn(stop -> stop.category().equals("맛집"))
                .isNotEmpty()
                .allSatisfy(stop -> assertThat(LocalTime.parse(stop.arrivalTime()))
                        .satisfiesAnyOf(
                                time -> assertThat(time).isBetween(LocalTime.of(11, 30), LocalTime.of(13, 30)),
                                time -> assertThat(time).isBetween(LocalTime.of(17, 30), LocalTime.of(19, 30))));
    }

    private TripQueryResult trip() {
        return new TripQueryResult(
                8L, 3L, "여행", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                DepartureMode.TOGETHER, null, null, 1L, null,
                TripStatus.PLANNING, null, null, null, null, null, 0, "invite");
    }

    private List<PlaceQueryResult> candidates() {
        return List.of(
                place(1, "경복궁", PlaceCategory.ATTRACTION, 37.5796, 126.9770),
                place(2, "광화문", PlaceCategory.RESTAURANT, 37.5700, 126.9769),
                place(3, "청계천", PlaceCategory.ATTRACTION, 37.5695, 126.9784),
                place(4, "인사동", PlaceCategory.SHOPPING, 37.5740, 126.9850),
                place(5, "북촌", PlaceCategory.CULTURE, 37.5826, 126.9830),
                place(6, "삼청동", PlaceCategory.CAFE, 37.5845, 126.9810),
                place(7, "대학로", PlaceCategory.CULTURE, 37.5820, 127.0020));
    }

    private PlaceQueryResult place(
            long id, String name, PlaceCategory category, double latitude, double longitude) {
        return new PlaceQueryResult(
                id, name, category, "서울", null, latitude, longitude, 1, "서울",
                null, null, "", false, null, null, null);
    }
}
