INSERT INTO surveys (id, survey_type, version, questions, status) VALUES
('10000000-0000-0000-0000-000000000001', 'PERSONALITY', '1.0', '[{"id":"pace","type":"SCALE"},{"id":"indoor","type":"SCALE"},{"id":"food","type":"SCALE"}]', 'ACTIVE');

INSERT INTO places (id, name, category, address, latitude, longitude, source, source_place_id, basic_info) VALUES
('20000000-0000-0000-0000-000000000001', '서울숲', 'PARK', '서울 성동구 뚝섬로 273', 37.5444000, 127.0374000, 'LOCAL', 'seoul-forest', '{"indoor":false,"pace":"RELAXED"}'),
('20000000-0000-0000-0000-000000000002', '국립중앙박물관', 'MUSEUM', '서울 용산구 서빙고로 137', 37.5238000, 126.9803000, 'LOCAL', 'national-museum', '{"indoor":true,"pace":"RELAXED"}'),
('20000000-0000-0000-0000-000000000003', '광장시장', 'MARKET', '서울 종로구 창경궁로 88', 37.5700000, 126.9997000, 'LOCAL', 'gwangjang-market', '{"indoor":false,"food":true}'),
('20000000-0000-0000-0000-000000000004', '서울도서관', 'SHELTER', '서울 중구 세종대로 110', 37.5662000, 126.9779000, 'LOCAL', 'seoul-library', '{"indoor":true,"shelter":true}');
