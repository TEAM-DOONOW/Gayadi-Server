package com.gayadi.server;

import com.gayadi.server.event.EventController;
import com.gayadi.server.invitation.InvitationController;
import com.gayadi.server.recommendation.PlaceRecommendationRequest;
import com.gayadi.server.schedule.ScheduleItemController;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ControllerRequestValidationTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void acceptsInitialPlanRevisionWhenDecidingProposal() {
        EventController.DecisionRequest request = new EventController.DecisionRequest();
        request.setApprove(false);
        request.setBaseRevisionNo(0);

        Assertions.assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejectsProposalDecisionWithoutRevision() {
        EventController.DecisionRequest request = new EventController.DecisionRequest();
        request.setApprove(true);

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
        InvitationController.CreateInvitationRequest request =
                new InvitationController.CreateInvitationRequest();

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("inviteeUserId");
    }

    @Test
    void rejectsScheduleTimeWithSeconds() {
        ScheduleItemController.CreateScheduleRequest request =
                new ScheduleItemController.CreateScheduleRequest();
        request.setTitle("일정");
        request.setDate("2026.08.20");
        request.setTime("10:30:45");
        request.setType(com.gayadi.server.schedule.ScheduleItemService.ScheduleType.MAIN);

        Assertions.assertThat(VALIDATOR.validate(request))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("time");
    }
}
