package com.gayadi.server.survey;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SurveyErrorCode implements ErrorCode {

    // Survey State - 진행 가능한 설문과 응답 상태
    SURVEY_ACTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_ACTIVE_NOT_FOUND",
            "error.survey.active-not-found", "진행 중인 성향 설문이 없습니다."),
    SURVEY_UNAVAILABLE(HttpStatus.CONFLICT, "SURVEY_UNAVAILABLE",
            "error.survey.unavailable", "진행할 수 있는 성향 설문이 없습니다."),
    SURVEY_RESPONSE_REQUIRED(HttpStatus.CONFLICT, "SURVEY_RESPONSE_REQUIRED",
            "error.survey.response-required", "성향 설문 응답이 필요합니다."),
    SURVEY_RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_RESPONSE_NOT_FOUND",
            "error.survey.response-not-found", "설문 응답을 찾을 수 없습니다."),

    // Submission - 제출 개수, 문항 및 선택지 검증
    SURVEY_ALL_ANSWERS_REQUIRED(HttpStatus.BAD_REQUEST, "SURVEY_ALL_ANSWERS_REQUIRED",
            "error.survey.all-answers-required", "활성 문항 {0}개에 모두 답변해야 합니다."),
    SURVEY_ANSWER_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "SURVEY_ANSWER_IDS_REQUIRED",
            "error.survey.answer-ids-required", "문항과 선택지 식별자가 모두 필요합니다."),
    SURVEY_ANSWER_COUNT_INVALID(HttpStatus.BAD_REQUEST, "SURVEY_ANSWER_COUNT_INVALID",
            "error.survey.answer-count-invalid", "활성 문항 {0}개에 모두 한 번씩 답변해야 합니다."),
    SURVEY_QUESTION_INACTIVE(HttpStatus.BAD_REQUEST, "SURVEY_QUESTION_INACTIVE",
            "error.survey.question-inactive", "활성 설문의 문항만 제출할 수 있습니다."),
    SURVEY_OPTION_MISMATCH(HttpStatus.BAD_REQUEST, "SURVEY_OPTION_MISMATCH",
            "error.survey.option-mismatch", "선택지가 해당 문항에 속하지 않습니다."),

    // Result - 성향 결과 조회와 일정 생성 선행 조건
    SURVEY_RESULT_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "SURVEY_RESULT_CODE_REQUIRED",
            "error.survey.result-code-required", "성향 결과 코드가 필요합니다."),
    SURVEY_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "SURVEY_RESULT_NOT_FOUND",
            "error.survey.result-not-found", "성향 검사 결과를 찾을 수 없습니다."),
    SURVEY_PROFILE_REQUIRED(HttpStatus.CONFLICT, "SURVEY_PROFILE_REQUIRED",
            "error.survey.profile-required", "일정 생성 전에 한 명 이상 성향 설문을 제출해야 합니다."),

    // Definition - 저장된 설문 정의의 무결성
    SURVEY_QUESTION_CATEGORY_INVALID(HttpStatus.CONFLICT, "SURVEY_QUESTION_CATEGORY_INVALID",
            "error.survey.question-category-invalid", "설문 문항의 분류가 올바르지 않습니다."),
    SURVEY_OPTION_SEQUENCE_INVALID(HttpStatus.CONFLICT, "SURVEY_OPTION_SEQUENCE_INVALID",
            "error.survey.option-sequence-invalid", "설문 선택지 순서가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    SurveyErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
    @Override public String defaultMessage() { return defaultMessage; }
}
