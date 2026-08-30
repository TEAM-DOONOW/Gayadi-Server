package com.gayadi.server.common.exception;

import com.gayadi.server.auth.AuthErrorCode;
import com.gayadi.server.auth.UserErrorCode;
import com.gayadi.server.congestion.CongestionErrorCode;
import com.gayadi.server.coordination.CoordinationErrorCode;
import com.gayadi.server.event.EventErrorCode;
import com.gayadi.server.expense.ExpenseErrorCode;
import com.gayadi.server.favorite.FavoriteErrorCode;
import com.gayadi.server.friendship.FriendshipErrorCode;
import com.gayadi.server.invitation.InvitationErrorCode;
import com.gayadi.server.legal.LegalErrorCode;
import com.gayadi.server.notice.NoticeErrorCode;
import com.gayadi.server.place.PlaceErrorCode;
import com.gayadi.server.recommendation.RecommendationErrorCode;
import com.gayadi.server.route.RouteErrorCode;
import com.gayadi.server.schedule.ScheduleErrorCode;
import com.gayadi.server.survey.SurveyErrorCode;
import com.gayadi.server.tourapi.TourApiErrorCode;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.weather.WeatherErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBundleCompletenessTest {

    private static final Pattern ARGUMENT = Pattern.compile("\\{\\d+}");

    @Test
    void everyErrorCodeHasMatchingKoreanAndEnglishMessages() throws IOException {
        Properties korean = loadBundles("");
        Properties english = loadBundles("_en");

        allErrorCodes().forEach(errorCode -> {
            String key = errorCode.messageKey();
            assertThat(korean.getProperty(key)).as("Korean message for %s", key).isNotBlank();
            assertThat(english.getProperty(key)).as("English message for %s", key).isNotBlank();
            assertThat(arguments(english.getProperty(key)))
                    .as("message arguments for %s", key)
                    .isEqualTo(arguments(korean.getProperty(key)));
        });
    }

    private Properties loadBundles(String localeSuffix) throws IOException {
        Properties result = new Properties();
        for (String domain : domains()) {
            String resource = "i18n/" + domain + "/messages" + localeSuffix + ".properties";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                result.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private Set<String> arguments(String message) {
        return ARGUMENT.matcher(message).results()
                .map(result -> result.group())
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<String> domains() {
        return List.of("common", "auth", "congestion", "coordination", "event", "expense",
                "favorite", "friendship", "invitation", "legal", "notice", "place",
                "recommendation", "route", "schedule", "survey", "tour", "travel", "weather");
    }

    private Stream<ErrorCode> allErrorCodes() {
        return Stream.of(
                        CommonErrorCode.values(), AuthErrorCode.values(), UserErrorCode.values(),
                        CongestionErrorCode.values(), CoordinationErrorCode.values(), EventErrorCode.values(),
                        ExpenseErrorCode.values(), FavoriteErrorCode.values(), FriendshipErrorCode.values(),
                        InvitationErrorCode.values(), LegalErrorCode.values(), NoticeErrorCode.values(),
                        PlaceErrorCode.values(), RecommendationErrorCode.values(), RouteErrorCode.values(),
                        ScheduleErrorCode.values(), SurveyErrorCode.values(), TourApiErrorCode.values(),
                        TripErrorCode.values(), WeatherErrorCode.values())
                .flatMap(Stream::of);
    }
}
