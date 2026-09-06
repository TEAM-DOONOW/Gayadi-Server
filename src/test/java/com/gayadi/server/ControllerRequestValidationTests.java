package com.gayadi.server;

import com.gayadi.server.auth.dto.request.GoogleLoginRequest;
import com.gayadi.server.event.dto.request.ChangeProposalDecisionRequest;
import com.gayadi.server.event.dto.request.EventObservationRequest;
import com.gayadi.server.event.model.Severity;
import com.gayadi.server.invitation.dto.request.InvitationCreateRequest;
import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.schedule.dto.request.CreateScheduleRequest;
import com.gayadi.server.schedule.dto.request.ScheduleOrderRequest;
import com.gayadi.server.schedule.model.ScheduleType;
import com.gayadi.server.survey.dto.request.SurveyResponseItem;
import com.gayadi.server.survey.dto.request.SurveyResponseRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;

class ControllerRequestValidationTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void rejectsBlankGoogleIdToken() {
        GoogleLoginRequest request = new GoogleLoginRequest("  ");

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("idToken");
    }

    @Test
    void acceptsInitialPlanRevisionWhenDecidingProposal() {
        ChangeProposalDecisionRequest request = new ChangeProposalDecisionRequest(
                false,
                null,
                0);

        Assertions.assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsProposalDecisionWithoutRevision() {
        ChangeProposalDecisionRequest request = new ChangeProposalDecisionRequest(
                true,
                null,
                null);

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("baseRevisionNo");
    }

    @Test
    void rejectsRecommendationOutsideCoordinateAndLimitRanges() {
        PlaceRecommendationRequest request = new PlaceRecommendationRequest();
        request.setProfile("한적한 곳을 좋아합니다.");
        request.setLatitude(91.0);
        request.setLongitude(-181.0);
        request.setLimit(21);

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("latitude", "longitude", "limit");
    }

    @Test
    void requiresTargetUserForIndividualInvitation() {
        InvitationCreateRequest request = new InvitationCreateRequest(null, null);

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("inviteeUserId");
    }

    @Test
    void rejectsScheduleTimeWithSeconds() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setTitle("일정");
        request.setDate("2026.08.20");
        request.setTime("10:30:45");
        request.setType(ScheduleType.MAIN);

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("time");
    }

    @Test
    void rejectsOversizedCollectionAndMapInputs() {
        ScheduleOrderRequest scheduleOrder = new ScheduleOrderRequest();
        scheduleOrder.setScheduleIds(Collections.nCopies(1001, 1L));

        SurveyResponseRequest survey = new SurveyResponseRequest();
        survey.setAnswers(Collections.nCopies(101, new SurveyResponseItem()));

        LinkedHashMap<String, Object> observationValues = new LinkedHashMap<>();
        for (int index = 0; index < 33; index++) {
            observationValues.put("field" + index, index);
        }
        EventObservationRequest observation = new EventObservationRequest(
                null,
                "WEATHER",
                "USER",
                Severity.LOW,
                observationValues);

        Assertions.assertThat(VALIDATOR.validate(scheduleOrder))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("scheduleIds");
        Assertions.assertThat(VALIDATOR.validate(survey))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("answers");
        Assertions.assertThat(VALIDATOR.validate(observation))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("values");
    }
}
