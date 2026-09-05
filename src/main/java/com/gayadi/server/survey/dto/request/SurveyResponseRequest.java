package com.gayadi.server.survey.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** SurveyResponseRequest API 요청 데이터를 전달합니다. */
@Schema(name = "SurveyResponseRequest", description = "성향 설문 제출 정보")
public class SurveyResponseRequest {

    @NotEmpty(message = "{validation.survey.answers.required}")
    @Size(max = 100, message = "{validation.survey.answers.size}")
    @Valid
    private List<SurveyResponseItem> answers;

    public List<SurveyResponseItem> getAnswers() {
        return answers;
    }

    public void setAnswers(List<SurveyResponseItem> answers) {
        this.answers = answers;
    }
}
