-- GAYADI ERDCloud Import DDL
-- 실제 실행 스키마: src/main/resources/db/migration/V1__create_gayadi_schema.sql
-- V2의 설문 1건과 서울 장소 4건은 개발용 기준/예시 데이터이며 스키마 정의가 아니다.

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    nickname VARCHAR(80) NOT NULL,
    oauth_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    oauth_subject VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_oauth UNIQUE (oauth_provider, oauth_subject)
);

CREATE TABLE trips (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    departure_mode VARCHAR(30) NOT NULL,
    departure_at TIMESTAMP NOT NULL,
    meeting_at TIMESTAMP,
    meeting_location VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trips_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE trip_members (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    participation_status VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    departure_location VARCHAR(1000) NOT NULL,
    return_destination VARCHAR(1000) NOT NULL,
    route_preferences VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_members_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_trip_member UNIQUE (trip_id, user_id)
);

CREATE TABLE surveys (
    id VARCHAR(36) PRIMARY KEY,
    survey_type VARCHAR(40) NOT NULL,
    version VARCHAR(20) NOT NULL,
    questions VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_survey_version UNIQUE (survey_type, version)
);

CREATE TABLE survey_responses (
    id VARCHAR(36) PRIMARY KEY,
    survey_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    trip_id VARCHAR(36),
    answers VARCHAR(4000) NOT NULL,
    result_code VARCHAR(40) NOT NULL,
    result_data VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_survey_responses_survey FOREIGN KEY (survey_id) REFERENCES surveys(id),
    CONSTRAINT fk_survey_responses_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_survey_responses_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT uk_trip_survey_user UNIQUE (trip_id, survey_id, user_id)
);

CREATE TABLE trip_plans (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL,
    survey_response_id VARCHAR(36),
    revision_no INTEGER NOT NULL DEFAULT 1,
    preference_snapshot VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_plans_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_plans_survey_response FOREIGN KEY (survey_response_id) REFERENCES survey_responses(id),
    CONSTRAINT uk_trip_plan UNIQUE (trip_id)
);

CREATE TABLE places (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(60) NOT NULL,
    address VARCHAR(300) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_place_id VARCHAR(120) NOT NULL,
    basic_info VARCHAR(2000),
    CONSTRAINT uk_place_source UNIQUE (source, source_place_id)
);

CREATE TABLE trip_plan_items (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL,
    place_id VARCHAR(36) NOT NULL,
    sequence_no INTEGER NOT NULL,
    planned_start TIMESTAMP NOT NULL,
    planned_end TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    CONSTRAINT fk_trip_plan_items_plan FOREIGN KEY (plan_id) REFERENCES trip_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_plan_items_place FOREIGN KEY (place_id) REFERENCES places(id),
    CONSTRAINT uk_plan_sequence UNIQUE (plan_id, sequence_no)
);

CREATE TABLE event_observations (
    id VARCHAR(36) PRIMARY KEY,
    place_id VARCHAR(36),
    event_type VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    observed_at TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    severity VARCHAR(20) NOT NULL,
    normalized_value VARCHAR(2000) NOT NULL,
    CONSTRAINT fk_event_observations_place FOREIGN KEY (place_id) REFERENCES places(id)
);

CREATE TABLE change_proposals (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    base_revision_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500) NOT NULL,
    options VARCHAR(4000) NOT NULL,
    selected_option VARCHAR(2000),
    before_snapshot VARCHAR(4000),
    after_snapshot VARCHAR(4000),
    decided_by VARCHAR(36),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_change_proposals_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_change_proposals_plan FOREIGN KEY (plan_id) REFERENCES trip_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_change_proposals_event FOREIGN KEY (event_id) REFERENCES event_observations(id),
    CONSTRAINT fk_change_proposals_decider FOREIGN KEY (decided_by) REFERENCES users(id)
);

CREATE TABLE trip_routes (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36),
    scope VARCHAR(30) NOT NULL,
    phase VARCHAR(30) NOT NULL,
    origin VARCHAR(1000) NOT NULL,
    destination VARCHAR(1000) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    transfer_count INTEGER NOT NULL,
    fare INTEGER NOT NULL,
    route_data VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECOMMENDED',
    valid_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_routes_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_routes_member FOREIGN KEY (member_id) REFERENCES trip_members(id) ON DELETE CASCADE
);

CREATE INDEX idx_trip_members_trip ON trip_members(trip_id);
CREATE INDEX idx_plan_items_plan ON trip_plan_items(plan_id, sequence_no);
CREATE INDEX idx_events_place_time ON event_observations(place_id, observed_at);
CREATE INDEX idx_routes_trip_phase ON trip_routes(trip_id, phase);
CREATE INDEX idx_proposals_trip_status ON change_proposals(trip_id, status);
