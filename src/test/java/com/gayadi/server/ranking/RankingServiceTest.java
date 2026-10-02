package com.gayadi.server.ranking;

import com.gayadi.server.ranking.dto.response.RankingItemResponse;
import com.gayadi.server.ranking.dto.response.RankingResponse;
import com.gayadi.server.tourapi.TourApiService;
import com.gayadi.server.tourapi.TourRegionResolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class RankingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private HttpServer server;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final List<String> queries = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void ranksAttractionsByHubRankExcludingFoodAndEnrichesImages() {
        responses.put("/hub/areaBasedList1", envelope("""
                [
                  {"baseYm":"202607","areaCd":"11","areaNm":"서울특별시","signguCd":"11110","signguNm":"종로구",
                   "hubTatsNm":"경복궁","hubCtgryLclsNm":"역사관광","hubCtgryMclsNm":"궁","hubRank":"2",
                   "mapX":"126.977","mapY":"37.579"},
                  {"baseYm":"202607","areaCd":"11","areaNm":"서울특별시","signguCd":"11140","signguNm":"중구",
                   "hubTatsNm":"명동교자","hubCtgryLclsNm":"음식","hubRank":"1","mapX":"126.98","mapY":"37.56"},
                  {"baseYm":"202607","areaCd":"11","areaNm":"서울특별시","signguCd":"11140","signguNm":"중구",
                   "hubTatsNm":"N서울타워","hubCtgryLclsNm":"랜드마크관광","hubRank":"1","mapX":"126.988","mapY":"37.551"}
                ]""", 3));
        responses.put("/tour/searchKeyword2", envelope("""
                [{"contentid":"126508","contenttypeid":"12","title":"경복궁","firstimage":"https://img/gbg.jpg",
                  "mapx":"126.977","mapy":"37.579"}]""", 1));

        RankingResponse response = service("key").rank(RankingType.ATTRACTION, "", 10);

        assertThat(response.region()).isEqualTo("서울");
        assertThat(response.providerDataAvailable()).isTrue();
        assertThat(response.source()).isEqualTo("KTO_DATALAB");
        assertThat(response.basePeriod()).isEqualTo("2026-07");
        assertThat(response.items()).extracting(RankingItemResponse::title)
                .containsExactly("N서울타워", "경복궁");
        RankingItemResponse palace = response.items().get(1);
        assertThat(palace.rank()).isEqualTo(2);
        assertThat(palace.subtitle()).isEqualTo("서울특별시 종로구 · 궁");
        assertThat(palace.latitude()).isEqualTo(37.579);
        assertThat(queries).anyMatch(query -> query.startsWith("/hub/areaBasedList1")
                && query.contains("baseYm=202607") && query.contains("areaCd=11"));
    }

    @Test
    void fallsBackToTourListWhenDataLabIsNotConfigured() {
        responses.put("/tour/areaBasedList2", envelope("""
                [{"contentid":"1","contenttypeid":"12","title":"덕수궁","addr1":"서울 중구 세종대로 99",
                  "firstimage":"https://img/dsg.jpg","mapx":"126.975","mapy":"37.565"}]""", 1));

        RankingResponse response = service("").rank(RankingType.ATTRACTION, "서울", 10);

        assertThat(response.providerDataAvailable()).isFalse();
        assertThat(response.source()).isEqualTo("TOUR_API");
        assertThat(response.items()).extracting(RankingItemResponse::title).containsExactly("덕수궁");
        assertThat(queries).noneMatch(query -> query.startsWith("/hub/"));
    }

    @Test
    void ordersFestivalsOngoingFirstThenBySoonestStartAndDropsEnded() {
        responses.put("/tour/searchFestival2", envelope("""
                [
                  {"contentid":"a","title":"다음달 축제","eventstartdate":"20261020","eventenddate":"20261025","addr1":"서울 마포구"},
                  {"contentid":"b","title":"진행 중 축제","eventstartdate":"20260920","eventenddate":"20260930","addr1":"서울 중구 을지로"},
                  {"contentid":"c","title":"이번주 축제","eventstartdate":"20260926","eventenddate":"20260927","addr1":"서울 강남구"},
                  {"contentid":"e","title":"하루 축제","eventstartdate":"20261001","eventenddate":"20261001","addr1":"서울 용산구"},
                  {"contentid":"d","title":"끝난 축제","eventstartdate":"20260901","eventenddate":"20260910","addr1":"서울 종로구"}
                ]""", 4));

        RankingResponse response = service("key").rank(RankingType.FESTIVAL, "서울", 10);

        assertThat(response.items()).extracting(RankingItemResponse::title)
                .containsExactly("진행 중 축제", "이번주 축제", "하루 축제", "다음달 축제");
        assertThat(response.items().get(2).subtitle()).isEqualTo("10.1 · 서울 용산구");
        assertThat(response.items().getFirst().subtitle()).isEqualTo("9.20 ~ 9.30 · 서울 중구");
        assertThat(response.items().getFirst().metricLabel()).isEqualTo("진행 중");
        assertThat(response.items().get(1).subtitle()).isEqualTo("9.26 ~ 9.27 · 서울 강남구");
        assertThat(queries).anyMatch(query -> query.startsWith("/tour/searchFestival2")
                && query.contains("eventStartDate=20260923") && query.contains("lDongRegnCd=11"));
    }

    @Test
    void festivalProviderFailureReturnsFallbackInsteadOfError() {
        RankingService service = new RankingService(
                new TourApiService(new ObjectMapper(), "", "http://127.0.0.1:" + server.getAddress().getPort() + "/tour", "Test"),
                new TourRegionResolver(null), new DataLabClient(new ObjectMapper(), "", "", "", "Test"), null, CLOCK);

        RankingResponse response = service.rank(RankingType.FESTIVAL, "서울", 10);

        assertThat(response.providerDataAvailable()).isFalse();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void ranksRegionsByNonLocalDailyVisitors() {
        responses.put("/visitor/locgoRegnVisitrDDList", envelope("""
                [
                  {"signguCode":"11110","signguNm":"종로구","areaNm":"서울특별시","touDivCd":"1","touNum":"999999","baseYmd":"20260910"},
                  {"signguCode":"11110","signguNm":"종로구","areaNm":"서울특별시","touDivCd":"2","touNum":"1000","baseYmd":"20260910"},
                  {"signguCode":"11110","signguNm":"종로구","areaNm":"서울특별시","touDivCd":"2","touNum":"3000","baseYmd":"20260911"},
                  {"signguCode":"26350","signguNm":"해운대구","areaNm":"부산광역시","touDivCd":"2","touNum":"5000","baseYmd":"20260910"},
                  {"signguCode":"26350","signguNm":"해운대구","areaNm":"부산광역시","touDivCd":"3","touNum":"1000","baseYmd":"20260910"}
                ]""", 5));

        RankingResponse nationwide = service("key").rank(RankingType.REGION, "", 10);

        assertThat(nationwide.items()).extracting(RankingItemResponse::title)
                .containsExactly("해운대구", "종로구");
        assertThat(nationwide.items().getFirst().metric()).isEqualTo(6000L);
        assertThat(nationwide.items().get(1).metric()).isEqualTo(2000L);
        assertThat(nationwide.items().get(1).metricLabel()).isEqualTo("일평균 방문 2,000명");
        assertThat(nationwide.basePeriod()).isEqualTo("2026-09-10~2026-09-16");

        RankingResponse seoul = service("key").rank(RankingType.REGION, "서울", 10);
        assertThat(seoul.items()).extracting(RankingItemResponse::title).containsExactly("종로구");
    }

    @Test
    void returnsEmptyRegionRankingWhenProviderHasNoData() {
        RankingResponse response = service("key").rank(RankingType.REGION, "", 10);

        assertThat(response.providerDataAvailable()).isFalse();
        assertThat(response.items()).isEmpty();
    }

    private RankingService service(String dataLabKey) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ObjectMapper mapper = new ObjectMapper();
        TourApiService tourApi = new TourApiService(mapper, "tour-key", base + "/tour", "Test");
        DataLabClient dataLab = new DataLabClient(mapper, dataLabKey, base + "/hub", base + "/visitor", "Test");
        return new RankingService(tourApi, new TourRegionResolver(tourApi), dataLab, null, CLOCK);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        queries.add(path + "?" + exchange.getRequestURI().getQuery());
        String body = responses.getOrDefault(path, envelope("[]", 0));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String envelope(String items, int total) {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                 "body":{"items":{"item":%s},"numOfRows":1000,"pageNo":1,"totalCount":%d}}}
                """.formatted(items, total);
    }
}
