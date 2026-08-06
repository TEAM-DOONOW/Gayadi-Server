-- 시드 데이터: 지역, 설문, 문항, 선택지, 장소

-- 지역
INSERT INTO regions (region_id, name, latitude, longitude, created_at, updated_at) VALUES
(1, '서울', 37.5665000, 126.9780000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 설문지
INSERT INTO surveys (id, name, description, version, status, created_at, updated_at) VALUES
(1, '여행 성향 검사', '여행 준비, 장소 선호, 여행 스타일을 파악하는 검사입니다.', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 설문 문항
INSERT INTO survey_questions (id, survey_id, question_text, axis_type, sequence_no, status, created_at, updated_at) VALUES
(1, 1, '여행 일정을 어떻게 준비하시나요?', 'TRAVEL_PREPARATION', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '어떤 장소를 선호하시나요?', 'PLACE_PREFERENCE', 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 1, '어떤 여행 스타일을 선호하시나요?', 'TRAVEL_STYLE', 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 설문 문항 선택지
INSERT INTO survey_question_options (id, question_id, option_text, option_code, score_value, sequence_no, created_at, updated_at) VALUES
(1, 1, '철저하게 계획하고 움직인다', 'PLANNED',  1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, '즉흥적으로 움직인다',       'SPONTANEOUS', -1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 2, '자연을 선호한다',           'NATURE',   1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 2, '도시를 선호한다',           'CITY',    -1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 3, '활동적으로 움직인다',       'ACTIVE',   1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 3, '여유롭게 즐긴다',           'RELAXED', -1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 장소
INSERT INTO places (id, source, visibility, name, category, address, latitude, longitude, region_id, basic_info, indoor, status, created_at, updated_at) VALUES
(1, 'TOUR_API', 'PUBLIC', '서울숲',        'ATTRACTION', '서울 성동구 뚝섬로 273',  37.5444000, 127.0374000, 1, '{"indoor":false,"pace":"RELAXED"}',  FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'TOUR_API', 'PUBLIC', '국립중앙박물관', 'CULTURE',   '서울 용산구 서빙고로 137', 37.5238000, 126.9803000, 1, '{"indoor":true,"pace":"RELAXED"}',   TRUE,  'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'TOUR_API', 'PUBLIC', '광장시장',       'SHOPPING',  '서울 종로구 창경궁로 88',  37.5700000, 126.9997000, 1, '{"indoor":false,"food":true}',      FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'TOUR_API', 'PUBLIC', '서울도서관',     'SHELTER',   '서울 중구 세종대로 110',   37.5662000, 126.9779000, 1, '{"indoor":true,"shelter":true}',    TRUE,  'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- IDENTITY 시퀀스 동기화 (명시적 ID 삽입 후)
ALTER TABLE users ALTER COLUMN id RESTART WITH 100;
ALTER TABLE regions ALTER COLUMN region_id RESTART WITH 100;
ALTER TABLE surveys ALTER COLUMN id RESTART WITH 100;
ALTER TABLE survey_questions ALTER COLUMN id RESTART WITH 100;
ALTER TABLE survey_question_options ALTER COLUMN id RESTART WITH 100;
ALTER TABLE places ALTER COLUMN id RESTART WITH 100;
