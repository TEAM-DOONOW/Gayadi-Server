package com.gayadi.server.friendship;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.friendship.dto.response.FriendshipResponse;
import com.gayadi.server.friendship.dto.response.PublicUserResponse;
import com.gayadi.server.friendship.dto.response.UserSearchResponse;
import com.gayadi.server.friendship.model.FriendshipStatus;
import com.gayadi.server.friendship.query.FriendshipQueryResult;
import com.gayadi.server.friendship.query.FriendshipStateQueryResult;
import com.gayadi.server.friendship.query.PublicUserQueryResult;
import com.gayadi.server.friendship.query.UserSearchQueryResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** 친구 관계 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class FriendshipService {
    private static final int MAX_LIST_SIZE = 100;
    private static final int MAX_SEARCH_SIZE = 30;
    private final FriendshipRepository repository;

    public FriendshipService(FriendshipRepository repository) {
        this.repository = repository;
    }

    /** 대상 사용자와 기존 관계를 검증해 친구 요청을 생성합니다. */
    @Transactional
    public FriendshipResponse create(long requesterId, long targetUserId) {
        if (requesterId == targetUserId) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_SELF_REQUEST);
        }
        lockActiveUsers(requesterId, targetUserId);
        long firstUserId = Math.min(requesterId, targetUserId);
        long secondUserId = Math.max(requesterId, targetUserId);
        FriendshipStateQueryResult current = repository
                .findPairForUpdate(firstUserId, secondUserId).orElse(null);
        if (current != null) {
            if (current.status() != FriendshipStatus.REJECTED) {
                throw new BusinessException(requestConflictCode(current.status()));
            }
            if (!repository.reopenRejected(current.id(), requesterId)) {
                throw changedConflict();
            }
            return detail(requesterId, current.id());
        }
        try {
            return detail(requesterId, repository.create(firstUserId, secondUserId, requesterId));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_REQUEST_CONFLICT);
        }
    }

    /** 사용자의 친구 관계를 상태와 페이지 조건으로 조회합니다. */
    public List<FriendshipResponse> list(long userId, String requestedStatus,
                                             int requestedLimit, int requestedOffset) {
        FriendshipStatus status = normalizeStatus(requestedStatus);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int offset = Math.max(0, requestedOffset);
        return repository.findAll(userId, status, limit, offset).stream()
                .map(result -> toResponse(result, userId)).toList();
    }

    /** 요청자 권한과 버전을 검증해 친구 관계 상태를 변경합니다. */
    @Transactional
    public FriendshipResponse update(long actorId, long friendshipId,
                                         FriendshipStatus targetStatus, Integer expectedVersion) {
        if (targetStatus == null || targetStatus == FriendshipStatus.PENDING) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_STATUS_INVALID);
        }
        FriendshipStateQueryResult current = lockedAccessible(friendshipId, actorId);
        if (expectedVersion != null && expectedVersion != current.version()) {
            throw changedConflict();
        }
        if (targetStatus == FriendshipStatus.ACCEPTED || targetStatus == FriendshipStatus.REJECTED) {
            if (current.status() != FriendshipStatus.PENDING) {
                throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_DECISION_NOT_PENDING);
            }
            if (actorId == current.requesterId()) {
                throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_DECISION_FORBIDDEN);
            }
        } else if (targetStatus == FriendshipStatus.BLOCKED
                && current.status() == FriendshipStatus.BLOCKED) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_ALREADY_BLOCKED);
        }
        Long blockedBy = targetStatus == FriendshipStatus.BLOCKED ? actorId : null;
        if (!repository.updateStatus(friendshipId, current.status(), targetStatus,
                blockedBy, current.version())) throw changedConflict();
        return detail(actorId, friendshipId);
    }

    /** 관계 당사자 권한과 버전을 검증해 친구 관계를 삭제합니다. */
    @Transactional
    public void delete(long actorId, long friendshipId) {
        FriendshipStateQueryResult current = lockedAccessible(friendshipId, actorId);
        if (current.status() == FriendshipStatus.PENDING && current.requesterId() != actorId) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_CANCEL_FORBIDDEN);
        }
        if (current.status() == FriendshipStatus.BLOCKED
                && !Long.valueOf(actorId).equals(current.blockedBy())) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        if (!repository.delete(friendshipId, current.version())) {
            throw changedConflict();
        }
    }

    /** 친구 추가 후보 사용자를 검색합니다. */
    public List<UserSearchResponse> searchUsers(long userId, String rawQuery, int requestedLimit) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_SEARCH_SIZE));
        return repository.searchUsers(userId, query, limit).stream()
                .map(result -> toSearchResponse(result, userId)).toList();
    }

    private void lockActiveUsers(long firstUserId, long secondUserId) {
        if (repository.lockActiveUsers(firstUserId, secondUserId).size() != 2) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_TARGET_USER_NOT_FOUND);
        }
    }

    private FriendshipStateQueryResult lockedAccessible(long friendshipId, long actorId) {
        FriendshipStateQueryResult result = repository.findForUpdate(friendshipId)
                .orElseThrow(() -> new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND));
        if (actorId != result.firstUserId() && actorId != result.secondUserId()) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        if (result.status() == FriendshipStatus.BLOCKED
                && !Long.valueOf(actorId).equals(result.blockedBy())) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        return result;
    }

    private FriendshipResponse detail(long actorId, long friendshipId) {
        return repository.findDetail(actorId, friendshipId)
                .map(result -> toResponse(result, actorId))
                .orElseThrow(() -> new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND));
    }

    private FriendshipResponse toResponse(FriendshipQueryResult result, long actorId) {
        boolean requestedByMe = result.requesterId() == actorId;
        return new FriendshipResponse(
                result.id(),
                toPublicUser(result.user()),
                result.status(),
                requestedByMe,
                result.status() == FriendshipStatus.PENDING && !requestedByMe,
                result.status() == FriendshipStatus.BLOCKED
                        && Long.valueOf(actorId).equals(result.blockedBy()),
                result.version(),
                result.decidedAt(),
                result.createdAt(),
                result.updatedAt());
    }

    private UserSearchResponse toSearchResponse(UserSearchQueryResult result, long actorId) {
        PublicUserQueryResult user = result.user();
        return new UserSearchResponse(
                user.id(),
                user.nickname(),
                user.introduction(),
                user.profileImageUrl(),
                user.characterKey(),
                user.emoji(),
                result.friendshipId(),
                result.friendshipStatus(),
                result.requesterId() != null && result.requesterId() == actorId,
                result.friendshipVersion());
    }

    private PublicUserResponse toPublicUser(PublicUserQueryResult user) {
        return new PublicUserResponse(
                user.id(),
                user.nickname(),
                user.introduction(),
                user.profileImageUrl(),
                user.characterKey(),
                user.emoji());
    }

    private FriendshipStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return FriendshipStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_STATUS_INVALID);
        }
    }

    private FriendshipErrorCode requestConflictCode(FriendshipStatus status) {
        return switch (status) {
            case PENDING -> FriendshipErrorCode.FRIENDSHIP_REQUEST_PENDING;
            case ACCEPTED -> FriendshipErrorCode.FRIENDSHIP_ALREADY_ACCEPTED;
            case BLOCKED -> FriendshipErrorCode.FRIENDSHIP_REQUEST_BLOCKED;
            case REJECTED -> throw new IllegalStateException("거절된 요청은 다시 보낼 수 있습니다.");
        };
    }

    private BusinessException changedConflict() {
        return new BusinessException(FriendshipErrorCode.FRIENDSHIP_CHANGED);
    }
}
