package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AndroidFeatureDomainHttpIntegrationTests {

    @LocalServerPort
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void androidDateExpenseNoticeAndInquiryJourneyUsesServerState() throws Exception {
        Account owner = register("android-owner-" + System.nanoTime() + "@example.com", "주최자");
        Account member = register("android-member-" + System.nanoTime() + "@example.com", "참여자");
        LocalDate first = LocalDate.now().plusDays(10);
        LocalDate last = first.plusDays(4);

        JsonNode trip = body(request("POST", "/api/v1/trips", owner.token(), """
                {"name":"Android 기능 여행","startDate":"%s","endDate":"%s","cities":["서울"]}
                """.formatted(first, last)), 201);
        long tripId = trip.path("id").asLong();
        String inviteCode = trip.path("inviteCode").asString();
        body(request("POST", "/api/v1/trip-memberships", member.token(),
                "{\"inviteCode\":\"" + inviteCode + "\"}"), 201);

        LocalDate commonStart = first.plusDays(1);
        LocalDate commonEnd = first.plusDays(2);
        String availability = """
                {"dates":["%s","%s","%s"]}
                """.formatted(first, commonStart, commonEnd);
        JsonNode ownerDates = body(request("PUT",
                "/api/v1/trips/" + tripId + "/date-coordination/availability/current",
                owner.token(), availability), 200);
        Assertions.assertThat(ownerDates.path("canFinalize").asBoolean()).isFalse();

        JsonNode incompleteSubmissions = body(request("PUT",
                "/api/v1/trips/" + tripId + "/date-coordination/finalized-dates",
                owner.token(), """
                        {"startDate":"%s","endDate":"%s"}
                        """.formatted(commonStart, commonEnd)), 409);
        assertError(incompleteSubmissions, 409, "COORDINATION_SUBMISSIONS_INCOMPLETE");

        JsonNode allDates = body(request("PUT",
                "/api/v1/trips/" + tripId + "/date-coordination/availability/current",
                member.token(), """
                        {"dates":["%s","%s","%s"]}
                        """.formatted(commonStart, commonEnd, commonEnd.plusDays(1))), 200);
        Assertions.assertThat(allDates.path("commonDates").size()).isEqualTo(2);

        JsonNode memberFinalize = body(request("PUT",
                "/api/v1/trips/" + tripId + "/date-coordination/finalized-dates",
                member.token(), """
                        {"startDate":"%s","endDate":"%s"}
                        """.formatted(commonStart, commonEnd)), 403);
        assertError(memberFinalize, 403, "TRIP_OWNER_REQUIRED");

        JsonNode finalized = body(request("PUT",
                "/api/v1/trips/" + tripId + "/date-coordination/finalized-dates",
                owner.token(), """
                        {"startDate":"%s","endDate":"%s"}
                        """.formatted(commonStart, commonEnd)), 200);
        Assertions.assertThat(finalized.path("startDate").asString())
                .isEqualTo(commonStart.toString().replace('-', '.'));
        Assertions.assertThat(finalized.path("participants").size()).isEqualTo(2);

        JsonNode schedule = body(request("POST",
                "/api/v1/trips/" + tripId + "/schedules", owner.token(), """
                        {
                          "title":"박물관 관람","date":"%s","time":"10:00",
                          "endTime":"11:30","memo":"정문에서 만나기","type":"MAIN"
                        }
                        """.formatted(commonStart)), 201);
        long scheduleId = schedule.path("id").asLong();
        Assertions.assertThat(schedule.path("endTime").asString()).isEqualTo("11:30");
        Assertions.assertThat(schedule.path("memo").asString()).isEqualTo("정문에서 만나기");

        JsonNode outsideTripSchedule = body(request("POST",
                "/api/v1/trips/" + tripId + "/schedules", owner.token(), """
                        {
                          "title":"여행 밖 일정","date":"%s","time":"10:00",
                          "type":"MAIN"
                        }
                        """.formatted(commonEnd.plusDays(1))), 400);
        assertError(outsideTripSchedule, 400, "SCHEDULE_DATE_OUTSIDE_TRIP");

        JsonNode updatedSchedule = body(request("PATCH",
                "/api/v1/trips/" + tripId + "/schedules/" + scheduleId, member.token(), """
                        {"time":"10:30","endTime":null,"memo":"입구가 아닌 정문"}
                        """), 200);
        Assertions.assertThat(updatedSchedule.path("time").asString()).isEqualTo("10:30");
        Assertions.assertThat(updatedSchedule.path("endTime").isNull()).isTrue();
        Assertions.assertThat(updatedSchedule.path("memo").asString()).isEqualTo("입구가 아닌 정문");

        JsonNode fund = body(request("POST",
                "/api/v1/trips/" + tripId + "/shared-fund/contributions",
                owner.token(), "{\"amount\":100000}"), 201);
        Assertions.assertThat(fund.path("balance").asLong()).isEqualTo(100_000L);

        JsonNode personal = body(request("POST",
                "/api/v1/trips/" + tripId + "/expenses", owner.token(), """
                        {
                          "scheduleId":%d,"title":"저녁 식사","memo":"여행 첫 식사","amount":10001,
                          "payerId":%d,"participantIds":[%d,%d],
                          "date":"%s","time":"18:30","category":"FOOD",
                          "paymentSource":"PERSONAL"
                        }
                        """.formatted(scheduleId, owner.id(), owner.id(), member.id(), commonStart)), 201);
        Assertions.assertThat(personal.path("participantIds").size()).isEqualTo(2);

        body(request("POST", "/api/v1/trips/" + tripId + "/expenses", member.token(), """
                {
                  "title":"입장권","amount":3000,"participantIds":[%d,%d],
                  "date":"%s","time":"10:00","category":"MUSEUM",
                  "paymentSource":"SHARED_FUND"
                }
                """.formatted(owner.id(), member.id(), commonEnd)), 201);

        JsonNode expenses = body(request(
                "GET", "/api/v1/trips/" + tripId + "/expenses", member.token(), null), 200);
        Assertions.assertThat(expenses.size()).isEqualTo(2);
        JsonNode settlement = body(request(
                "GET", "/api/v1/trips/" + tripId + "/expense-settlement", owner.token(), null), 200);
        Assertions.assertThat(settlement.path("totalAmount").asLong()).isEqualTo(13_001L);
        Assertions.assertThat(settlement.path("transfers").size()).isEqualTo(1);
        Assertions.assertThat(settlement.path("transfers").get(0).path("amount").asLong())
                .isEqualTo(5_000L);
        JsonNode balance = body(request(
                "GET", "/api/v1/trips/" + tripId + "/shared-fund", owner.token(), null), 200);
        Assertions.assertThat(balance.path("balance").asLong()).isEqualTo(97_000L);

        JsonNode inquiry = body(request("POST", "/api/v1/inquiries", member.token(), """
                {"category":"bug","title":"화면 문의","message":"재현 내용을 전달합니다.",
                 "contactEmail":"support-user@example.com"}
                """), 201);
        Assertions.assertThat(inquiry.path("category").asString()).isEqualTo("bug");
        Assertions.assertThat(inquiry.path("status").asString()).isEqualTo("RECEIVED");

        JsonNode notices = body(request("GET", "/api/v1/notices", null, null), 200);
        Assertions.assertThat(notices).isNotEmpty();
        String noticeId = notices.get(0).path("id").asString();
        JsonNode notice = body(request("GET", "/api/v1/notices/" + noticeId, null, null), 200);
        Assertions.assertThat(notice.path("sections")).isNotEmpty();
        Assertions.assertThat(notice.has("isPinned")).isTrue();
    }

    @Test
    void openApiContainsEveryNewAndroidDomainContract() throws Exception {
        JsonNode openApi = body(request("GET", "/api/openapi", null, null), 200);
        JsonNode paths = openApi.path("paths");
        Assertions.assertThat(paths.has("/api/v1/trips/{tripId}/date-coordination")).isTrue();
        Assertions.assertThat(paths.has("/api/v1/trips/{tripId}/expenses")).isTrue();
        Assertions.assertThat(paths.has("/api/v1/trips/{tripId}/expense-settlement")).isTrue();
        Assertions.assertThat(paths.has("/api/v1/trips/{tripId}/shared-fund/contributions")).isTrue();
        Assertions.assertThat(paths.has("/api/v1/notices")).isTrue();
        Assertions.assertThat(paths.has("/api/v1/inquiries")).isTrue();
        Assertions.assertThat(paths.path("/api/v1/trips/{tripId}/expenses").path("get")
                .path("security").toString()).contains("bearerAuth");
        JsonNode scheduleProperties = openApi.path("components").path("schemas")
                .path("ScheduleResponse").path("properties");
        Assertions.assertThat(scheduleProperties.has("endTime")).isTrue();
        Assertions.assertThat(scheduleProperties.has("memo")).isTrue();
    }

    @Test
    void expenseBusinessFailuresUseStableErrorCodes() throws Exception {
        Account owner = register("expense-errors-" + System.nanoTime() + "@example.com", "경비검증");
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(2);
        JsonNode trip = body(request("POST", "/api/v1/trips", owner.token(), """
                {"name":"경비 오류 검증","startDate":"%s","endDate":"%s","cities":["서울"]}
                """.formatted(start, end)), 201);
        long tripId = trip.path("id").asLong();

        JsonNode duplicateParticipant = body(request("POST",
                "/api/v1/trips/" + tripId + "/expenses", owner.token(), """
                        {
                          "title":"중복 분담","amount":1000,"payerId":%d,
                          "participantIds":[%d,%d],"date":"%s","time":"12:00",
                          "category":"OTHER","paymentSource":"PERSONAL"
                        }
                        """.formatted(owner.id(), owner.id(), owner.id(), start)), 400);
        assertError(duplicateParticipant, 400, "EXPENSE_PARTICIPANT_DUPLICATED");

        JsonNode insufficientFund = body(request("POST",
                "/api/v1/trips/" + tripId + "/expenses", owner.token(), """
                        {
                          "title":"공동 경비 초과","amount":1000,
                          "participantIds":[%d],"date":"%s","time":"12:00",
                          "category":"OTHER","paymentSource":"SHARED_FUND"
                        }
                        """.formatted(owner.id(), start)), 409);
        assertError(insufficientFund, 409, "SHARED_FUND_BALANCE_INSUFFICIENT");

        JsonNode missingExpense = body(request("DELETE",
                "/api/v1/trips/" + tripId + "/expenses/999999999",
                owner.token(), null), 404);
        assertError(missingExpense, 404, "EXPENSE_NOT_FOUND");
    }

    private Account register(String email, String nickname) throws Exception {
        JsonNode response = body(request("POST", "/api/v1/auth/registrations", null,
                "{\"email\":\"" + email
                        + "\",\"password\":\"password1\",\"nickname\":\""
                        + nickname + "\"}"), 201);
        return new Account(response.path("user").path("id").asLong(),
                response.path("accessToken").asString());
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response, int expectedStatus) {
        Assertions.assertThat(response.statusCode())
                .withFailMessage("HTTP %s: %s", response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    private void assertError(JsonNode response, int status, String code) {
        Assertions.assertThat(response.path("status").asInt()).isEqualTo(status);
        Assertions.assertThat(response.path("code").asString()).isEqualTo(code);
        Assertions.assertThat(response.path("traceId").asString()).isNotBlank();
        Assertions.assertThat(response.path("details").isNull()).isTrue();
    }

    private record Account(long id, String token) {
    }
}
