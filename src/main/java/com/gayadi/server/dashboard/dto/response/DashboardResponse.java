package com.gayadi.server.dashboard.dto.response;

import com.gayadi.server.event.dto.response.ChangeProposalResponse;
import com.gayadi.server.schedule.dto.response.ScheduleResponse;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.dto.response.TripResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 여행 홈에 필요한 여행·참여자·일정·변경 제안 정보를 반환합니다. */
@Schema(name = "DashboardResponse", description = "여행 홈 통합 정보")
public record DashboardResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        TripResponse trip,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<TripDayResponse> tripDays,

        @Schema(description = "여행 시작일까지 남은 일수", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        long daysUntilStart,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ParticipantResponse> participants,

        @Schema(description = "참여자 수", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        int participantCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ScheduleResponse> schedules,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ScheduleResponse> todaySchedules,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DashboardProgressResponse progress,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ChangeProposalResponse> pendingChangeProposals,

        @Schema(description = "응답 생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime generatedAt
) {
}
