package com.gayadi.server.survey.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** SurveyResponseItem API 요청 데이터를 전달합니다. */
@Schema(name = "SurveyResponseItem", description = "성향 설문 개별 문항 응답")
public class SurveyResponseItem {

    @NotNull(message = "{validation.survey.question-id.required}")
    @Pattern(regexp = "q\\d{2}", message = "{validation.survey.question-id.pattern}")
    private String questionId;

    @NotNull(message = "{validation.survey.option-id.required}")
    @Pattern(regexp = "[a-z]", message = "{validation.survey.option-id.pattern}")
    private String optionId;

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }
}
