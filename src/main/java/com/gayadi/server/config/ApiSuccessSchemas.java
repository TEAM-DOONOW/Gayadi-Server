package com.gayadi.server.config;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Map 기반 성공 응답을 OpenAPI에 정확히 보여 주기 위한 문서 전용 형식입니다.
 * 실제 응답 객체로 사용하지 않으며, 컨트롤러의 성공 응답 설명에서만 참조합니다.
 */
public final class ApiSuccessSchemas {

    private ApiSuccessSchemas() {
    }

    @Schema(name = "AuthTokenResponse", description = "가입 또는 로그인으로 발급한 인증 정보")
    public record AuthToken(
            @Schema(description = "API 인증에 사용할 JWT") String accessToken,
            @Schema(description = "인증 방식", example = "Bearer") String tokenType,
            @Schema(description = "토큰 만료까지 남은 초", example = "7200") long expiresIn,
            Account user
    ) {
    }

    @Schema(name = "AccountResponse", description = "로그인한 계정의 기본 정보")
    public record Account(
            @Schema(description = "사용자 번호", example = "12") long id,
            @Schema(description = "닉네임", example = "가야디") String nickname,
            @Schema(description = "이메일", example = "traveler@example.com") String email,
            @Schema(description = "한 줄 소개", example = "천천히 걷는 여행을 좋아해요.") String introduction,
            @Schema(description = "프로필 이미지 주소") String profile_image_url,
            @Schema(description = "계정 상태", example = "ACTIVE") String status,
            @Schema(description = "마지막 로그인 시각") LocalDateTime last_login_at,
            @Schema(description = "계정 생성 시각") LocalDateTime created_at,
            @Schema(description = "계정 수정 시각") LocalDateTime updated_at
    ) {
    }

    @Schema(name = "UserProfileResponse", description = "현재 사용자의 공개 프로필과 최신 여행 성향")
    public record UserProfile(
            @Schema(description = "사용자 번호", example = "12") long id,
            @Schema(description = "이메일", example = "traveler@example.com") String email,
            @Schema(description = "닉네임", example = "가야디") String nickname,
            @Schema(description = "한 줄 소개") String introduction,
            @Schema(description = "프로필 이미지 주소") String profileImageUrl,
            @Schema(description = "최신 성향 결과 코드", example = "PNR") String resultCode,
            @Schema(description = "최신 성향 이름") String travelStyleName,
            @Schema(description = "앱 캐릭터 자료 식별자", example = "character_pnr") String characterKey,
            @Schema(description = "성향의 강점") List<String> strengths,
            @Schema(description = "성향에서 주의할 점") List<String> weaknesses
    ) {
    }

    @Schema(name = "TripResponse", description = "여행 기본 정보")
    public record Trip(
            @Schema(description = "여행 번호", example = "31") long id,
            @Schema(description = "여행 이름", example = "제주도 우정 여행") String name,
            @Schema(description = "여행 시작일", example = "2026.08.20") String startDate,
            @Schema(description = "여행 종료일", example = "2026.08.22") String endDate,
            @Schema(description = "방문 도시") List<String> cities,
            @Schema(description = "여행 상태", allowableValues = {"PLANNING", "ONGOING", "COMPLETED"}) String status,
            @Schema(description = "여행 소유자 번호", example = "12") long ownerId,
            @Schema(description = "참여 사용자 번호") List<Long> participantIds,
            @Schema(description = "여행 공유 코드", example = "U7K9P2") String inviteCode,
            @Schema(description = "동시 수정 확인용 버전", example = "0") int version,
            @Schema(description = "여행 생성 시각") LocalDateTime createdAt,
            @Schema(description = "여행 수정 시각") LocalDateTime updatedAt
    ) {
    }

    @Schema(name = "ParticipantResponse", description = "여행 참여자 정보")
    public record Participant(
            @Schema(description = "앱에서 사용하는 사용자 번호", example = "12") long id,
            @Schema(description = "사용자 번호", example = "12") long userId,
            @Schema(description = "서버 내부 참여자 번호", example = "45") long participantId,
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "앱 캐릭터 자료 식별자") String characterKey,
            @Schema(description = "여행 역할", allowableValues = {"OWNER", "MEMBER"}) String role,
            @Schema(description = "참여 상태", example = "JOINED") String status,
            @Schema(description = "개별 출발 장소 번호") Long departurePlaceId,
            @Schema(description = "개별 귀가 장소 번호") Long returnPlaceId,
            @Schema(description = "참여한 여행 번호") Long tripId
    ) {
    }

    @Schema(name = "InvitationResponse", description = "특정 사용자에게 발급한 여행 초대")
    public record Invitation(
            @Schema(description = "초대 번호", example = "27") long id,
            @Schema(description = "여행 번호", example = "31") long tripId,
            @Schema(description = "초대한 사용자 번호", example = "12") long inviterId,
            @Schema(description = "초대한 사용자 닉네임") String inviterNickname,
            @Schema(description = "초대받은 사용자 번호", example = "18") Long inviteeId,
            @Schema(description = "초대받은 사용자 닉네임") String inviteeNickname,
            @Schema(description = "특정 사용자 초대 코드", example = "I8M3K9Q2") String code,
            @Schema(description = "초대 상태", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "CANCELLED"}) String status,
            @Schema(description = "초대 만료 시각") LocalDateTime expiresAt,
            @Schema(description = "수락 시각") LocalDateTime acceptedAt,
            @Schema(description = "거절 시각") LocalDateTime declinedAt,
            @Schema(description = "취소 시각") LocalDateTime cancelledAt,
            @Schema(description = "초대 생성 시각") LocalDateTime createdAt
    ) {
    }

    @Schema(name = "MembershipResponse", description = "초대 코드로 여행에 참여한 결과")
    public record Membership(
            @Schema(description = "특정 사용자 초대 번호. 여행 공유 코드로 참여하면 없습니다.") Long invitationId,
            Trip trip,
            Participant participant
    ) {
    }

    @Schema(name = "ScheduleResponse", description = "앱에서 직접 편집하는 일정")
    public record Schedule(
            @Schema(description = "일정 번호", example = "81") long id,
            @Schema(description = "여행 번호", example = "31") long tripId,
            @Schema(description = "일정 제목", example = "성산일출봉") String title,
            @Schema(description = "연결한 장소 번호") Long placeId,
            @Schema(description = "연결한 장소 이름") String placeName,
            @Schema(description = "일정 날짜", example = "2026.08.20") String date,
            @Schema(description = "일정 시각", example = "09:30") String time,
            @Schema(description = "일정 종류", allowableValues = {"MAIN", "ALTERNATIVE"}) String type,
            @Schema(description = "여행 전체 일정에서의 순서. 0부터 시작합니다.", example = "0") int order,
            @Schema(description = "방문 완료 여부", example = "false") boolean isVisited
    ) {
    }

    @Schema(name = "PlanResponse", description = "자동으로 만든 여행 일정. 첫날 정보와 전체 일차를 함께 제공합니다.")
    public record Plan(
            @Schema(description = "첫날 일정 번호") long id,
            @Schema(description = "여행 번호") long trip_id,
            @Schema(description = "첫날의 일차") int day_number,
            @Schema(description = "첫날 날짜") LocalDate plan_date,
            @Schema(description = "일정 상태", example = "DRAFT") String status,
            @Schema(description = "전체 일차별 일정") List<PlanDay> days
    ) {
    }

    @Schema(name = "PlanDayResponse", description = "하루 단위 자동 일정")
    public record PlanDay(
            long id,
            long trip_id,
            @Schema(description = "여행 몇 일차인지", example = "1") int day_number,
            LocalDate plan_date,
            String status,
            @Schema(description = "해당 일차의 방문 순서") List<PlanItem> items
    ) {
    }

    @Schema(name = "PlanItemResponse", description = "자동 일정에 들어간 장소 또는 활동")
    public record PlanItem(
            long id,
            int sequence_no,
            LocalDateTime planned_start,
            LocalDateTime planned_end,
            String status,
            String item_type,
            String title,
            String description,
            Integer estimated_cost,
            String memo,
            Long place_id,
            String place_name,
            String category,
            String address,
            Double latitude,
            Double longitude
    ) {
    }

    @Schema(name = "PlacePageResponse", description = "커서 방식으로 나눈 공개 장소 목록")
    public record PlacePage(
            List<Place> items,
            @Schema(description = "다음 목록의 기준값. 다음 목록이 없으면 없습니다.") Long nextCursor,
            @Schema(description = "다음 목록이 있는지", example = "true") boolean hasNext
    ) {
    }

    @Schema(name = "PlaceResponse", description = "공개 장소 정보")
    public record Place(
            @Schema(description = "장소 번호", example = "101") long id,
            @Schema(description = "장소 이름") String name,
            @Schema(description = "앱에 표시할 한국어 장소 분류") String category,
            @Schema(description = "검색 조건에 쓰는 장소 분류 코드", allowableValues = {"ATTRACTION", "RESTAURANT", "ACCOMMODATION", "CAFE", "SHELTER", "CULTURE", "SHOPPING", "ETC"}) String categoryCode,
            @Schema(description = "평점 자료가 없으면 0") double rating,
            @Schema(description = "후기 수 자료가 없으면 0") int reviews,
            @Schema(description = "후기 수") int reviewCount,
            @Schema(description = "평점과 후기 자료 제공 여부") boolean ratingAvailable,
            @Schema(description = "혼잡도", allowableValues = {"RELAXED", "NORMAL", "CROWDED"}) String crowdLevel,
            @Schema(description = "실시간 혼잡 자료 제공 여부") boolean crowdDataAvailable,
            @Schema(description = "분류에 맞는 표시 그림") String emoji,
            @Schema(description = "장소 설명") String description,
            String address,
            String roadAddress,
            Double latitude,
            Double longitude,
            long regionId,
            String regionName,
            String phone,
            String homepageUrl,
            String imageUrl,
            Boolean indoor,
            String basicInfo,
            String operatingHours,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(name = "FavoritePlaceResponse", description = "사용자가 찜한 장소")
    public record FavoritePlace(
            long id,
            String name,
            String category,
            String address,
            String roadAddress,
            Double latitude,
            Double longitude,
            long regionId,
            String phone,
            String homepageUrl,
            String imageUrl,
            Boolean indoor,
            @Schema(description = "장소 설명") String description,
            @Schema(description = "사용자가 남긴 메모") String memo,
            @Schema(description = "찜한 시각") LocalDateTime favoritedAt
    ) {
    }

    @Schema(name = "SurveyResponse", description = "여행 성향 설문 문항과 결과 종류")
    public record Survey(
            @Schema(example = "travel-personality-v1") String id,
            String title,
            String description,
            int version,
            @Schema(example = "active") String status,
            @Schema(description = "결과 코드를 구성하는 성향 축의 순서") List<String> resultCodeOrder,
            List<SurveyQuestion> questions,
            List<PersonalityResult> results
    ) {
    }

    @Schema(name = "SurveyQuestionResponse", description = "여행 성향 설문 문항")
    public record SurveyQuestion(
            @Schema(example = "q01") String id,
            String title,
            @Schema(allowableValues = {"preparation", "place", "energy"}) String dimension,
            int order,
            List<SurveyOption> options
    ) {
    }

    @Schema(name = "SurveyOptionResponse", description = "설문 문항 선택지")
    public record SurveyOption(
            @Schema(example = "a") String id,
            String text,
            String code
    ) {
    }

    @Schema(name = "PersonalityResultResponse", description = "여행 성향 결과")
    public record PersonalityResult(
            @Schema(example = "PNR") String code,
            String emoji,
            String name,
            String summary,
            String characterKey,
            List<String> hashtags,
            List<String> strengths,
            List<String> weaknesses,
            List<CompatibleType> compatibleTypes,
            TravelRole travelRole
    ) {
    }

    @Schema(name = "CompatiblePersonalityResponse", description = "함께 여행하기 좋은 성향")
    public record CompatibleType(String code, String emoji, String name) {
    }

    @Schema(name = "TravelRoleResponse", description = "여행에서 잘 맡는 역할")
    public record TravelRole(String icon, String title, String description) {
    }

    @Schema(name = "SurveySubmissionResponse", description = "여행 성향 답변을 채점한 결과")
    public record SurveySubmission(
            long attemptId,
            Long tripId,
            @Schema(example = "PNR") String resultCode,
            int preparationScore,
            int placeScore,
            int energyScore,
            PersonalityResult result
    ) {
    }

    @Schema(name = "GroupPersonalityResponse", description = "여행 참여자의 성향 분포")
    public record GroupPersonality(
            @Schema(example = "PNR") String dominantProfile,
            long responseCount,
            @Schema(description = "성향 코드별 제출 인원") Map<String, Long> distribution
    ) {
    }

    @Schema(name = "RouteResponse", description = "추천하거나 선택한 이동 경로")
    public record Route(
            long id,
            String optionId,
            String name,
            long tripId,
            long planId,
            @Schema(description = "앱에서 사용하는 사용자 번호") Long memberId,
            Long userId,
            @Schema(description = "서버 내부 참여자 번호") Long participantId,
            @Schema(allowableValues = {"GROUP", "MEMBER"}) String scope,
            @Schema(allowableValues = {"DEPARTURE", "ITINERARY", "HOME"}) String type,
            @Schema(allowableValues = {"DEPARTURE", "IN_TRIP", "RETURN"}) String phase,
            Location origin,
            Location destination,
            List<Location> stops,
            List<RouteSegment> segments,
            Integer durationMinutes,
            Integer distanceMeters,
            Integer transferCount,
            Integer fare,
            String transportMode,
            String status,
            String provider,
            String summary,
            LocalDateTime recommendedAt,
            LocalDateTime selectedAt,
            RouteData routeData,
            @Schema(description = "선택할 수 있는 추천안") List<Route> options
    ) {
    }

    @Schema(name = "RouteLocationResponse", description = "경로의 장소와 좌표")
    public record Location(String name, double latitude, double longitude) {
    }

    @Schema(name = "RouteSegmentResponse", description = "경로 안의 한 이동 구간")
    public record RouteSegment(
            int order,
            Location origin,
            Location destination,
            int durationMinutes,
            int transferCount,
            int fare,
            String summary
    ) {
    }

    @Schema(name = "RouteDataResponse", description = "선택한 경로에 저장된 계산 자료")
    public record RouteData(
            String provider,
            String optionId,
            String optionName,
            String summary,
            Location origin,
            Location destination,
            List<Location> stops,
            List<RouteSegment> segments
    ) {
    }

    @Schema(name = "DashboardResponse", description = "여행 홈에 필요한 자료")
    public record Dashboard(
            Trip trip,
            List<TripDay> tripDays,
            @Schema(description = "여행 시작일까지 남은 날짜. 시작 뒤에는 음수입니다.") long daysUntilStart,
            List<Participant> participants,
            int participantCount,
            List<Schedule> schedules,
            List<Schedule> todaySchedules,
            DashboardProgress progress,
            List<ChangeProposal> pendingChangeProposals,
            LocalDateTime generatedAt
    ) {
    }

    @Schema(name = "TripDayResponse", description = "여행의 날짜별 표시 정보")
    public record TripDay(int dayNumber, String date, String dateLabel) {
    }

    @Schema(name = "DashboardProgressResponse", description = "일정 방문 진행률")
    public record DashboardProgress(int scheduleCount, long visitedCount, int percentage) {
    }

    @Schema(name = "ChangeProposalSummaryResponse", description = "처리하지 않은 일정 변경 제안 요약")
    public record ChangeProposal(
            long id,
            String type,
            String reason,
            String status,
            Integer baseRevisionNo,
            @Schema(description = "선택할 수 있는 변경안") List<ChangeProposalOption> options,
            LocalDateTime generatedAt
    ) {
    }

    @Schema(name = "ChangeProposalOptionResponse", description = "일정 변경 제안에서 선택할 수 있는 대체 장소")
    public record ChangeProposalOption(
            String key,
            long placeId,
            String placeName,
            String description
    ) {
    }

    @Schema(name = "EventObservationResponse", description = "일정 변경이 필요하지 않은 현장 상황 등록 결과")
    public record EventObservation(
            long eventId,
            boolean impact,
            String message
    ) {
    }

    @Schema(name = "ChangeProposalResponse", description = "현장 상황에 따라 만든 일정 변경 제안")
    public record ChangeProposalDetail(
            long id,
            long tripId,
            long planId,
            long eventId,
            String type,
            String reason,
            String status,
            int baseRevisionNo,
            List<ChangeProposalOption> options,
            String selectedOptionKey,
            Long decidedBy,
            LocalDateTime generatedAt,
            LocalDateTime decidedAt,
            LocalDateTime appliedAt,
            Object before,
            Object after
    ) {
    }

    @Schema(name = "PublicUserResponse", description = "다른 사용자에게 공개하는 프로필")
    public record PublicUser(
            long id,
            String nickname,
            String introduction,
            String profileImageUrl,
            String characterKey,
            String emoji
    ) {
    }

    @Schema(name = "FriendshipResponse", description = "현재 사용자 기준 친구 관계")
    public record Friendship(
            long id,
            PublicUser user,
            @Schema(allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "BLOCKED"}) String status,
            boolean requestedByMe,
            boolean canDecide,
            boolean blockedByMe,
            int version,
            LocalDateTime decidedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @Schema(name = "UserSearchResponse", description = "친구 추가용 사용자 검색 결과")
    public record UserSearch(
            long id,
            String nickname,
            String introduction,
            String profileImageUrl,
            String characterKey,
            String emoji,
            Long friendshipId,
            String friendshipStatus,
            boolean requestedByMe,
            Integer friendshipVersion
    ) {
    }

    @Schema(name = "LegalDocumentResponse", description = "공개 중인 법률 문서")
    public record LegalDocument(
            @Schema(example = "privacy-policy") String id,
            String title,
            String version,
            LocalDate effectiveDate,
            @Schema(example = "PUBLISHED") String publicationStatus,
            String summary,
            List<LegalSection> sections,
            String reviewNotice
    ) {
    }

    @Schema(name = "LegalSectionResponse", description = "법률 문서의 한 항목")
    public record LegalSection(String title, String body) {
    }
}
