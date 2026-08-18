-- 반복 로그인 실패로부터 계정을 보호한다.
ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users
    ADD COLUMN login_locked_until TIMESTAMP;
ALTER TABLE users
    ADD CONSTRAINT ck_user_failed_login_attempts CHECK (failed_login_attempts BETWEEN 0 AND 4);
