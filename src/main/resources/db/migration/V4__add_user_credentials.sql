-- GAYADI Schema V4 — 로그인 자격 증명 추가
-- 기존 로컬 개발용 사용자(nickname만)와 호환되도록 email/password_hash는 NULL 허용
ALTER TABLE users ADD COLUMN email VARCHAR(255);
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- PostgreSQL/H2의 기본 비교는 대소문자를 구분하므로 저장값도 소문자로 통일한다.
UPDATE users SET email = LOWER(email) WHERE email IS NOT NULL;
ALTER TABLE users
    ADD CONSTRAINT ck_users_email_lowercase CHECK (email IS NULL OR email = LOWER(email));

-- PostgreSQL/H2 모두 NULL은 unique 대상에서 제외되므로 기존 행과 충돌하지 않는다
CREATE UNIQUE INDEX uk_users_email ON users (email);

-- 로그인 식별자(이메일) 조회 인덱스
CREATE INDEX idx_users_email ON users (email);
