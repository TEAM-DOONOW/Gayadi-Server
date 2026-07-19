package com.gayadi.server.survey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SurveyController {
    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping("/surveys/personality")
    Map<String, Object> personalitySurvey() {
        return service.personalitySurvey();
    }

    @PostMapping("/trips/{tripId}/survey-responses")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> respond(@PathVariable String tripId, @Valid @RequestBody ResponseRequest request) {
        return service.respond(tripId, request.userId(), request.answers());
    }

    @GetMapping("/trips/{tripId}/personality-profile")
    Map<String, Object> groupProfile(@PathVariable String tripId) {
        return service.groupProfile(tripId);
    }

    public record ResponseRequest(@NotBlank String userId, @NotEmpty Map<String, Integer> answers) {
    }
}
