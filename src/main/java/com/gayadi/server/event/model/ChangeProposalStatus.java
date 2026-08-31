package com.gayadi.server.event.model;

/** 일정 변경 제안의 대기·승인·거절·만료 상태를 나타냅니다. */
public enum ChangeProposalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}
