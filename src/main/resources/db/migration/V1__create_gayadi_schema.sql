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
    owner_id VARCHAR(36) NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL,
    departure_mode VARCHAR(30) NOT NULL,
    departure_at TIMESTAMP NOT NULL,
    meeting_at TIMESTAMP,
    meeting_location VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trip_members (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    participation_status VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    departure_location VARCHAR(1000) NOT NULL,
    return_destination VARCHAR(1000) NOT NULL,
    route_preferences VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    survey_id VARCHAR(36) NOT NULL REFERENCES surveys(id),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    trip_id VARCHAR(36) REFERENCES trips(id) ON DELETE CASCADE,
    answers VARCHAR(4000) NOT NULL,
    result_code VARCHAR(40) NOT NULL,
    result_data VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_trip_survey_user UNIQUE (trip_id, survey_id, user_id)
);

CREATE TABLE trip_plans (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    survey_response_id VARCHAR(36) REFERENCES survey_responses(id),
    revision_no INTEGER NOT NULL DEFAULT 1,
    preference_snapshot VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    plan_id VARCHAR(36) NOT NULL REFERENCES trip_plans(id) ON DELETE CASCADE,
    place_id VARCHAR(36) NOT NULL REFERENCES places(id),
    sequence_no INTEGER NOT NULL,
    planned_start TIMESTAMP NOT NULL,
    planned_end TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    CONSTRAINT uk_plan_sequence UNIQUE (plan_id, sequence_no)
);

CREATE TABLE event_observations (
    id VARCHAR(36) PRIMARY KEY,
    place_id VARCHAR(36) REFERENCES places(id),
    event_type VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    observed_at TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    severity VARCHAR(20) NOT NULL,
    normalized_value VARCHAR(2000) NOT NULL
);

CREATE TABLE change_proposals (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    plan_id VARCHAR(36) NOT NULL REFERENCES trip_plans(id) ON DELETE CASCADE,
    event_id VARCHAR(36) NOT NULL REFERENCES event_observations(id),
    base_revision_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500) NOT NULL,
    options VARCHAR(4000) NOT NULL,
    selected_option VARCHAR(2000),
    before_snapshot VARCHAR(4000),
    after_snapshot VARCHAR(4000),
    decided_by VARCHAR(36) REFERENCES users(id),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trip_routes (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    member_id VARCHAR(36) REFERENCES trip_members(id) ON DELETE CASCADE,
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_members_trip ON trip_members(trip_id);
CREATE INDEX idx_plan_items_plan ON trip_plan_items(plan_id, sequence_no);
CREATE INDEX idx_events_place_time ON event_observations(place_id, observed_at);
CREATE INDEX idx_routes_trip_phase ON trip_routes(trip_id, phase);
CREATE INDEX idx_proposals_trip_status ON change_proposals(trip_id, status);
