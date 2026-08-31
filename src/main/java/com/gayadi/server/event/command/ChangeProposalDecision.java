package com.gayadi.server.event.command;

/** 일정 변경 제안에 대한 사용자의 승인 또는 거절 결정을 전달합니다. */
public record ChangeProposalDecision(
        boolean approve,
        String selectedOptionKey,
        int baseRevisionNo,
        long decidedBy
) {
}
