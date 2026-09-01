package com.gayadi.server.event.query;

/** DB snapshot에서 복원한 일정 변경 장소 선택지입니다. */
public record ChangeProposalOptionQueryResult(
        String key,
        long placeId,
        String placeName,
        String description,
        boolean requireIndoor
) {
}
