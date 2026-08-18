-- 공개 장소 검색과 여행별 경로 선택에서 자주 쓰는 조건을 함께 조회한다.
CREATE INDEX idx_places_public_lookup
    ON places (visibility, status, region_id, category, id);

CREATE INDEX idx_travel_routes_selection
    ON travel_routes (plan_id, phase, member_id, status, id);
