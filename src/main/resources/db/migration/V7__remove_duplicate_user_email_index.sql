-- 유일 인덱스가 이메일 조회에도 쓰이므로 같은 열의 일반 인덱스는 제거한다.
DROP INDEX IF EXISTS idx_users_email;
