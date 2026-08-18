-- 초기 스키마에서 선택값이었던 참여 인원과 초대 코드를 Android 계약에 맞춰 보완한다.
UPDATE trips t
SET max_members = CASE
    WHEN (SELECT COUNT(*) FROM trip_participants tp
          WHERE tp.trip_id = t.id AND tp.status = 'JOINED') > 100 THEN 100
    WHEN (SELECT COUNT(*) FROM trip_participants tp
          WHERE tp.trip_id = t.id AND tp.status = 'JOINED') > 20
        THEN CAST((SELECT COUNT(*) FROM trip_participants tp
                   WHERE tp.trip_id = t.id AND tp.status = 'JOINED') AS INT)
    ELSE 20
END
WHERE max_members IS NULL;

ALTER TABLE trips ALTER COLUMN max_members SET DEFAULT 20;
ALTER TABLE trips ALTER COLUMN max_members SET NOT NULL;
