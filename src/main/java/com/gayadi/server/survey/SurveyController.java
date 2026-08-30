package com.gayadi.server.survey;

import com.gayadi.server.common.dto.ApiResponseMapper;
import com.gayadi.server.common.dto.ApiResponses;
import com.gayadi.server.travel.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "설문", description = "여행 성향 문항, 제출 결과와 여행별 성향을 제공합니다.")
public class SurveyController {

    private final SurveyService service;
    private final TripService trips;
    private final ApiResponseMapper mapper;

    public SurveyController(SurveyService service, TripService trips, ApiResponseMapper mapper) {
        this.service = service;
        this.trips = trips;
        this.mapper = mapper;
    }

    @GetMapping("/surveys/travel-personality-v1")
    @Operation(summary = "여행 성향 설문 조회")
    @ApiResponse(responseCode = "200", description = "여행 성향 문항과 결과 종류입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Survey.class)))
    public ApiResponses.Survey personalitySurvey() {
        return mapper.toDto(service.personalitySurvey(), ApiResponses.Survey.class);
    }

    @GetMapping("/surveys/travel-personality-v1/results/{resultCode}")
    @Operation(summary = "여행 성향 결과 조회")
    @ApiResponse(responseCode = "200", description = "여행 성향 결과의 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.PersonalityResult.class)))
    public ApiResponses.PersonalityResult result(@PathVariable String resultCode) {
        return mapper.toDto(service.result(resultCode), ApiResponses.PersonalityResult.class);
    }

    @PostMapping("/surveys/travel-personality-v1/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내 여행 성향 답변 제출")
    @ApiResponse(responseCode = "201", description = "답변을 채점한 여행 성향 결과입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.SurveySubmission.class)))
    public ApiResponses.SurveySubmission submission(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ResponseRequest request) {
        return mapper.toDto(
                service.respond(null, userId, request.getAnswers()),
                ApiResponses.SurveySubmission.class);
    }

    @PostMapping("/trips/{tripId}/survey-responses")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "여행별 성향 답변 제출")
    @ApiResponse(responseCode = "201", description = "여행에 저장한 성향 답변 결과입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.SurveySubmission.class)))
    public ApiResponses.SurveySubmission tripSubmission(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody ResponseRequest request) {
        return mapper.toDto(
                service.respond(Long.valueOf(tripId), userId, request.getAnswers()),
                ApiResponses.SurveySubmission.class);
    }

    @GetMapping("/trips/{tripId}/personality-profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "여행 참여자 성향 요약")
    @ApiResponse(responseCode = "200", description = "여행 참여자의 성향 분포입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.GroupPersonality.class)))
    public ApiResponses.GroupPersonality groupProfile(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        trips.requireMember(tripId, userId);
        return mapper.toDto(service.groupProfile(tripId), ApiResponses.GroupPersonality.class);
    }

    public static class ResponseRequest {
        @NotEmpty
        @Valid
        private List<ResponseItem> answers;

        public List<ResponseItem> getAnswers() { return answers; }
        public void setAnswers(List<ResponseItem> answers) { this.answers = answers; }
    }

    public static class ResponseItem {
        @NotNull
        @Pattern(regexp = "q\\d{2}", message = "문항 식별자 형식이 올바르지 않습니다.")
        private String questionId;
        @NotNull
        @Pattern(regexp = "[a-z]", message = "선택지 식별자 형식이 올바르지 않습니다.")
        private String optionId;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        public String getOptionId() { return optionId; }
        public void setOptionId(String optionId) { this.optionId = optionId; }
    }
}
