-- GAYADI ERDCloud Import DDL
-- MySQL 8.x 기준
--
-- 모델을 의도적으로 단순화한 버전이다.
-- - 독립적인 업무 단위만 테이블로 분리한다.
-- - 설문 문항/답변, 경로 구간, 외부 API 상세 응답은 JSON으로 보관한다.
-- - places와 event_observations는 나중에 별도 DB로 분리할 수 있다.
-- - 아래에서는 ERDCloud에서 관계를 보이기 위해 논리 FK를 둔다.
-- - 실제 실행 스키마의 기준은 src/main/resources/db/migration 이며, 이 파일은 발표용 논리 ERD다.

-- ============================================================
-- 1. 사용자 / 여행
-- ============================================================

CREATE TABLE users (
    id                  CHAR(36) PRIMARY KEY,
    nickname            VARCHAR(50) NOT NULL,
    oauth_provider      VARCHAR(30) NOT NULL,
    oauth_subject       VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_users_oauth (oauth_provider, oauth_subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trips (
    id                  CHAR(36) PRIMARY KEY,
    owner_id            CHAR(36) NOT NULL,
    title               VARCHAR(100) NOT NULL,
    destination_name    VARCHAR(100) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    departure_mode      VARCHAR(20) NOT NULL,
    departure_at        DATETIME(3) NOT NULL,
    meeting_at          DATETIME(3) NULL,
    meeting_address     VARCHAR(255) NULL,
    meeting_latitude    DECIMAL(10,7) NULL,
    meeting_longitude   DECIMAL(10,7) NULL,
    invite_code         VARCHAR(100) NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_trips_owner
        FOREIGN KEY (owner_id) REFERENCES users(id),
    UNIQUE KEY uq_trips_invite_code (invite_code),
    KEY idx_trips_owner_status (owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_members (
    id                    CHAR(36) PRIMARY KEY,
    trip_id               CHAR(36) NOT NULL,
    user_id               CHAR(36) NOT NULL,
    role                  VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    participation_status  VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    departure_location    JSON NULL,
    return_destination    JSON NULL,
    route_preferences     JSON NULL,
    joined_at             DATETIME(3) NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_trip_members_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_trip_members_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_trip_members_trip_user (trip_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. 설문 / 장소
-- ============================================================

CREATE TABLE surveys (
    id                  CHAR(36) PRIMARY KEY,
    survey_type         VARCHAR(30) NOT NULL,
    title               VARCHAR(100) NOT NULL,
    version             VARCHAR(30) NOT NULL,
    questions           JSON NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_surveys_type_version (survey_type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE survey_responses (
    id                  CHAR(36) PRIMARY KEY,
    survey_id           CHAR(36) NOT NULL,
    user_id             CHAR(36) NOT NULL,
    trip_id             CHAR(36) NOT NULL,
    answers             JSON NULL,
    result_code         VARCHAR(50) NULL,
    result_data         JSON NULL,
    completed_at        DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_survey_responses_survey
        FOREIGN KEY (survey_id) REFERENCES surveys(id),
    CONSTRAINT fk_survey_responses_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_survey_responses_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id),
    UNIQUE KEY uq_survey_responses_trip_user (trip_id, survey_id, user_id),
    KEY idx_survey_responses_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE places (
    id                  CHAR(36) PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    category            VARCHAR(50) NOT NULL,
    address             VARCHAR(255) NULL,
    latitude            DECIMAL(10,7) NOT NULL,
    longitude           DECIMAL(10,7) NOT NULL,
    source              VARCHAR(50) NULL,
    source_place_id     VARCHAR(255) NULL,
    basic_info          JSON NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    synced_at           DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_places_source (source, source_place_id),
    KEY idx_places_category (category),
    KEY idx_places_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 여행 일정
-- ============================================================

CREATE TABLE trip_plans (
    id                    CHAR(36) PRIMARY KEY,
    trip_id               CHAR(36) NOT NULL,
    survey_response_id    CHAR(36) NULL,
    revision_no           INT NOT NULL DEFAULT 1,
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    preference_snapshot   JSON NULL,
    confirmed_at          DATETIME(3) NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_trip_plans_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_trip_plans_survey_response
        FOREIGN KEY (survey_response_id) REFERENCES survey_responses(id),
    UNIQUE KEY uq_trip_plans_trip (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_plan_items (
    id                  CHAR(36) PRIMARY KEY,
    plan_id             CHAR(36) NOT NULL,
    place_id            CHAR(36) NOT NULL,
    sequence_no         INT NOT NULL,
    planned_start_at    DATETIME(3) NULL,
    planned_end_at      DATETIME(3) NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    memo                VARCHAR(500) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_trip_plan_items_plan
        FOREIGN KEY (plan_id) REFERENCES trip_plans(id),
    CONSTRAINT fk_trip_plan_items_place
        FOREIGN KEY (place_id) REFERENCES places(id),
    UNIQUE KEY uq_trip_plan_items_sequence (plan_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. 사용자가 선택한 경로
-- ============================================================

CREATE TABLE trip_routes (
    id                    CHAR(36) PRIMARY KEY,
    trip_id               CHAR(36) NOT NULL,
    member_id             CHAR(36) NULL,
    scope                 VARCHAR(20) NOT NULL,
    phase                 VARCHAR(20) NOT NULL,
    origin                JSON NOT NULL,
    destination           JSON NOT NULL,
    departure_at          DATETIME(3) NOT NULL,
    primary_mode          VARCHAR(30) NOT NULL,
    duration_seconds      INT NOT NULL,
    distance_meters       INT NULL,
    transfer_count        INT NOT NULL DEFAULT 0,
    fare                  INT NULL,
    arrival_at            DATETIME(3) NULL,
    route_data            JSON NULL,
    provider              VARCHAR(50) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    valid_until           DATETIME(3) NULL,
    selected_at           DATETIME(3) NOT NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_trip_routes_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_trip_routes_member
        FOREIGN KEY (member_id) REFERENCES trip_members(id),
    KEY idx_trip_routes_trip_phase (trip_id, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 날씨 / 혼잡 / 교통 이벤트
-- ============================================================

CREATE TABLE event_observations (
    id                  CHAR(36) PRIMARY KEY,
    event_type          VARCHAR(30) NOT NULL,
    source              VARCHAR(50) NOT NULL,
    place_id            CHAR(36) NULL,
    grid_key            VARCHAR(100) NULL,
    observed_at         DATETIME(3) NOT NULL,
    valid_from          DATETIME(3) NULL,
    valid_to            DATETIME(3) NULL,
    severity            VARCHAR(20) NULL,
    normalized_value    JSON NOT NULL,
    source_updated_at   DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_event_observations_place
        FOREIGN KEY (place_id) REFERENCES places(id),
    KEY idx_event_observations_place_time (place_id, observed_at),
    KEY idx_event_observations_type_time (event_type, observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE change_proposals (
    id                    CHAR(36) PRIMARY KEY,
    trip_id               CHAR(36) NOT NULL,
    plan_id               CHAR(36) NOT NULL,
    event_id              CHAR(36) NULL,
    base_revision_no      INT NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason                VARCHAR(1000) NOT NULL,
    options               JSON NOT NULL,
    selected_option_key   VARCHAR(50) NULL,
    before_snapshot       JSON NOT NULL,
    after_snapshot        JSON NULL,
    estimated_delta_seconds INT NULL,
    decided_by            CHAR(36) NULL,
    decided_at            DATETIME(3) NULL,
    notified_at           DATETIME(3) NULL,
    expires_at            DATETIME(3) NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_change_proposals_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_change_proposals_plan
        FOREIGN KEY (plan_id) REFERENCES trip_plans(id),
    CONSTRAINT fk_change_proposals_event
        FOREIGN KEY (event_id) REFERENCES event_observations(id),
    CONSTRAINT fk_change_proposals_decider
        FOREIGN KEY (decided_by) REFERENCES users(id),
    KEY idx_change_proposals_trip_status (trip_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
