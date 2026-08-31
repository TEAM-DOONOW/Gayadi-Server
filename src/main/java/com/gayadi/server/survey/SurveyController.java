package com.gayadi.server.survey;

import com.gayadi.server.survey.dto.request.SurveyResponseRequest;
import com.gayadi.server.survey.dto.response.GroupPersonalityResponse;
import com.gayadi.server.survey.dto.response.PersonalityResultResponse;
import com.gayadi.server.survey.dto.response.SurveyResponse;
import com.gayadi.server.survey.dto.response.SurveySubmissionResponse;
import com.gayadi.server.travel.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 여행 성향 설문 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "설문", description = "여행 성향 문항, 제출 결과와 여행별 성향을 제공합니다.")
public class SurveyController {

    private final SurveyService service;
    private final TripService trips;

    public SurveyController(SurveyService service, TripService trips) {
        this.service = service;
        this.trips = trips;
    }

    @GetMapping("/surveys/travel-personality-v1")
    @Operation(summary = "여행 성향 설문 조회")
    @ApiResponse(responseCode = "200", description = "여행 성향 문항과 결과 종류입니다.",
            content = @Content(schema = @Schema(implementation = SurveyResponse.class)))
    public SurveyResponse personalitySurvey() {
        return service.personalitySurvey();
    }

    @GetMapping("/surveys/travel-personality-v1/results/{resultCode}")
    @Operation(summary = "여행 성향 결과 조회")
    @ApiResponse(responseCode = "200", description = "여행 성향 결과의 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = PersonalityResultResponse.class)))
    public PersonalityResultResponse result(@PathVariable String resultCode) {
        return service.result(resultCode);
    }

    @PostMapping("/surveys/travel-personality-v1/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내 여행 성향 답변 제출")
    @ApiResponse(responseCode = "201", description = "답변을 채점한 여행 성향 결과입니다.",
            content = @Content(schema = @Schema(implementation = SurveySubmissionResponse.class)))
    public SurveySubmissionResponse submission(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SurveyResponseRequest request) {
        return service.respond(null, userId, request.getAnswers());
    }

    @PostMapping("/trips/{tripId}/survey-responses")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "여행별 성향 답변 제출")
    @ApiResponse(responseCode = "201", description = "여행에 저장한 성향 답변 결과입니다.",
            content = @Content(schema = @Schema(implementation = SurveySubmissionResponse.class)))
    public SurveySubmissionResponse tripSubmission(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody SurveyResponseRequest request) {
        return service.respond(Long.valueOf(tripId), userId, request.getAnswers());
    }

    @GetMapping("/trips/{tripId}/personality-profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "여행 참여자 성향 요약")
    @ApiResponse(responseCode = "200", description = "여행 참여자의 성향 분포입니다.",
            content = @Content(schema = @Schema(implementation = GroupPersonalityResponse.class)))
    public GroupPersonalityResponse groupProfile(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        trips.requireMember(tripId, userId);
        return service.groupProfile(tripId);
    }

}
