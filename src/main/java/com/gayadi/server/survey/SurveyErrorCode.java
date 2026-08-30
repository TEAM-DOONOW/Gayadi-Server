package com.gayadi.server.survey;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SurveyErrorCode implements ErrorCode {

    // Survey State - 진행 가능한 설문과 응답 상태
    SURVEY_ACTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_ACTIVE_NOT_FOUND",
            "error.survey.active-not-found"),
    SURVEY_UNAVAILABLE(HttpStatus.CONFLICT, "SURVEY_UNAVAILABLE",
            "error.survey.unavailable"),
    SURVEY_RESPONSE_REQUIRED(HttpStatus.CONFLICT, "SURVEY_RESPONSE_REQUIRED",
            "error.survey.response-required"),
    SURVEY_RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_RESPONSE_NOT_FOUND",
            "error.survey.response-not-found"),

    // Submission - 제출 개수, 문항 및 선택지 검증
    SURVEY_ALL_ANSWERS_REQUIRED(HttpStatus.BAD_REQUEST, "SURVEY_ALL_ANSWERS_REQUIRED",
            "error.survey.all-answers-required"),
    SURVEY_ANSWER_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "SURVEY_ANSWER_IDS_REQUIRED",
            "error.survey.answer-ids-required"),
    SURVEY_ANSWER_COUNT_INVALID(HttpStatus.BAD_REQUEST, "SURVEY_ANSWER_COUNT_INVALID",
            "error.survey.answer-count-invalid"),
    SURVEY_QUESTION_INACTIVE(HttpStatus.BAD_REQUEST, "SURVEY_QUESTION_INACTIVE",
            "error.survey.question-inactive"),
    SURVEY_OPTION_MISMATCH(HttpStatus.BAD_REQUEST, "SURVEY_OPTION_MISMATCH",
            "error.survey.option-mismatch"),

    // Result - 성향 결과 조회와 일정 생성 선행 조건
    SURVEY_RESULT_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "SURVEY_RESULT_CODE_REQUIRED",
            "error.survey.result-code-required"),
    SURVEY_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_RESULT_NOT_FOUND",
            "error.survey.result-not-found"),
    SURVEY_PROFILE_REQUIRED(HttpStatus.CONFLICT, "SURVEY_PROFILE_REQUIRED",
            "error.survey.profile-required"),

    // Definition - 저장된 설문 정의의 무결성
    SURVEY_QUESTION_CATEGORY_INVALID(HttpStatus.CONFLICT, "SURVEY_QUESTION_CATEGORY_INVALID",
            "error.survey.question-category-invalid"),
    SURVEY_OPTION_SEQUENCE_INVALID(HttpStatus.CONFLICT, "SURVEY_OPTION_SEQUENCE_INVALID",
            "error.survey.option-sequence-invalid");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    SurveyErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
