package com.gayadi.server.event.command;

/** 상황대처 Agent가 일정 변경 후보로 선택한 내부 장소를 나타냅니다. */
public record AiChangeProposalOption(
        long placeId,
        String description
) {
}
