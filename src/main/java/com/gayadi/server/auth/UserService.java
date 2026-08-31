package com.gayadi.server.auth;

import com.gayadi.server.auth.dto.response.UserProfileResponse;
import com.gayadi.server.auth.query.UserPersonalityQueryResult;
import com.gayadi.server.auth.query.UserQueryResult;
import com.gayadi.server.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 인증과 사용자 계정 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** 닉네임을 사용해 기본 사용자 계정을 생성합니다. */
    public UserQueryResult create(String nickname) {
        return get(repository.create(nickname.trim()));
    }

    /** 사용자 조건에 맞는 사용자 정보를 조회합니다. */
    public UserQueryResult get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    /** 이메일 정보를 조회합니다. */
    public Optional<UserQueryResult> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    /** 식별자 정보를 조회합니다. */
    public Optional<UserQueryResult> findById(long id) {
        return repository.findById(id);
    }

    /** 성향에 대한 사용자 기능을 처리합니다. */
    public UserProfileResponse profile(long id) {
        UserQueryResult user = get(id);
        UserPersonalityQueryResult personality = repository.findLatestPersonality(id).orElse(null);
        return new UserProfileResponse(
                user.id(),
                user.email(),
                user.nickname(),
                user.introduction(),
                user.profileImageUrl(),
                personality == null ? null : personality.resultCode(),
                personality == null ? null : personality.name(),
                personality == null ? null : personality.characterKey(),
                personality == null ? null : personality.strengths(),
                personality == null ? null : personality.weaknesses());
    }

    /** 사용자 사용자 상태를 변경합니다. */
    @Transactional
    public UserProfileResponse update(long id, String nickname, String introduction) {
        if (!repository.updateProfile(id, nickname.trim(), trimToNull(introduction))) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return profile(id);
    }

    /** 사용자 탈퇴와 개인정보 비식별화를 처리합니다. */
    @Transactional
    public void withdraw(long id) {
        lockActive(id);
        if (repository.countActiveOwnedTrips(id) > 0) {
            throw new BusinessException(UserErrorCode.USER_ACTIVE_OWNED_TRIP_EXISTS);
        }
        repository.removeUserRelations(id);
        if (!repository.anonymize(id)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /** 활성 여부나 개수를 확인합니다. */
    public boolean isActive(long id) {
        return repository.isActive(id);
    }

    /** 사용자 사용자 접근 조건을 검증합니다. */
    public void requireExists(long id) {
        if (!repository.isActive(id)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /** 변경 충돌을 막기 위해 활성 DB 행을 잠급니다. */
    public void lockActive(long id) {
        if (!repository.lockActive(id)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
