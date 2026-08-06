package com.gayadi.server.survey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SurveyController {

    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping("/surveys/personality")
    public Map<String, Object> personalitySurvey() {
        return service.personalitySurvey();
    }

    @PostMapping("/trips/{tripId}/survey-responses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> respond(
            @PathVariable long tripId,
            @Valid @RequestBody ResponseRequest request) {
        return service.respond(tripId, request.getUserId(), request.getResponses());
    }

    @GetMapping("/trips/{tripId}/personality-profile")
    public Map<String, Object> groupProfile(@PathVariable long tripId) {
        return service.groupProfile(tripId);
    }

    public static class ResponseRequest {
        @NotNull
        private Long userId;
        @NotEmpty
        @Valid
        private List<ResponseItem> responses;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public List<ResponseItem> getResponses() { return responses; }
        public void setResponses(List<ResponseItem> responses) { this.responses = responses; }
    }

    public static class ResponseItem {
        @NotNull
        private Long questionId;
        @NotNull
        private Long optionId;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Long getOptionId() { return optionId; }
        public void setOptionId(Long optionId) { this.optionId = optionId; }
    }
}
