-- Android 앱의 여행별 공유 코드는 여러 참여자가 여행 준비 중 재사용할 수 있다.
-- 기존 여행은 기존 초대 API를 계속 쓸 수 있도록 NULL을 허용하고 새 여행부터 발급한다.
ALTER TABLE trips
    ADD COLUMN invite_code VARCHAR(6);

ALTER TABLE trips
    ADD CONSTRAINT ck_trip_invite_code_length
        CHECK (invite_code IS NULL OR CHAR_LENGTH(invite_code) = 6);

CREATE UNIQUE INDEX uk_trips_invite_code ON trips (invite_code);
