package com.gayadi.server.invitation.query;

import com.gayadi.server.travel.model.TripStatus;

/** 여행 초대 Repository의 InvitationTripQueryResult 조회 결과를 전달합니다. */
public record InvitationTripQueryResult(
        long id,
        TripStatus status,
        Integer maxMembers
) {
}
