package com.gayadi.server.common.exception;

import com.gayadi.server.coordination.CoordinationErrorCode;
import com.gayadi.server.auth.AuthErrorCode;
import com.gayadi.server.auth.UserErrorCode;
import com.gayadi.server.congestion.CongestionErrorCode;
import com.gayadi.server.legal.LegalErrorCode;
import com.gayadi.server.notice.NoticeErrorCode;
import com.gayadi.server.place.PlaceErrorCode;
import com.gayadi.server.recommendation.RecommendationErrorCode;
import com.gayadi.server.survey.SurveyErrorCode;
import com.gayadi.server.weather.WeatherErrorCode;
import com.gayadi.server.tourapi.TourApiErrorCode;
import com.gayadi.server.route.RouteErrorCode;
import com.gayadi.server.expense.ExpenseErrorCode;
import com.gayadi.server.event.EventErrorCode;
import com.gayadi.server.favorite.FavoriteErrorCode;
import com.gayadi.server.friendship.FriendshipErrorCode;
import com.gayadi.server.invitation.InvitationErrorCode;
import com.gayadi.server.schedule.ScheduleErrorCode;
import com.gayadi.server.travel.TripErrorCode;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeUniquenessTest {

    @Test
    void publicCodesAreUniqueAcrossDomains() {
        Stream<ErrorCode> codes = Stream.of(
                        CommonErrorCode.values(),
                        AuthErrorCode.values(),
                        UserErrorCode.values(),
                        CongestionErrorCode.values(),
                        LegalErrorCode.values(),
                        NoticeErrorCode.values(),
                        PlaceErrorCode.values(),
                        RecommendationErrorCode.values(),
                        SurveyErrorCode.values(),
                        WeatherErrorCode.values(),
                        TourApiErrorCode.values(),
                        RouteErrorCode.values(),
                        CoordinationErrorCode.values(),
                        ExpenseErrorCode.values(),
                        EventErrorCode.values(),
                        FavoriteErrorCode.values(),
                        FriendshipErrorCode.values(),
                        InvitationErrorCode.values(),
                        ScheduleErrorCode.values(),
                        TripErrorCode.values())
                .flatMap(Stream::of);

        assertThat(codes.map(ErrorCode::code)).doesNotHaveDuplicates();
    }
}
