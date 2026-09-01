package com.gayadi.server.survey.query;

/** 여행 성향 설문 Repository의 SurveyQueryResults 조회 결과를 전달합니다. */
public final class SurveyQueryResults {

    private SurveyQueryResults() {
    }

    public record Header(
            long id,
            String name,
            String description,
            int version,
            String status
    ) {
    }

    public record QuestionOption(
            long questionId,
            String questionText,
            String axisType,
            int questionSequence,
            Long optionId,
            String optionText,
            String optionCode,
            Integer scoreValue,
            Integer optionSequence
    ) {
    }

    public record PersonalityResult(
            String resultCode,
            String emoji,
            String name,
            String summary,
            String characterKey,
            String hashtagsJson,
            String strengthsJson,
            String weaknessesJson,
            String compatibleTypesJson,
            String travelRoleJson
    ) {
    }

    public record ActiveQuestion(
            long questionId,
            String axisType,
            int sequence
    ) {
    }

    public record Option(
            long optionId,
            long questionId,
            int scoreValue,
            int optionSequence,
            int questionSequence
    ) {
    }

    public record Attempt(
            long id,
            Long tripId,
            String resultCode,
            int preparationScore,
            int placeScore,
            int energyScore
    ) {
    }

    public record ProfileCount(
            String resultCode,
            long responseCount
    ) {
    }
}
