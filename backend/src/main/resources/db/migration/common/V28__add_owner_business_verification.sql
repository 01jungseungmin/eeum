-- 기존 입력값을 검증 증빙으로 간주하지 않는다. 재심사 시 서버에서 다시 검증한다.
ALTER TABLE owner_info
    ADD COLUMN business_verified_at DATETIME(6) NULL,
    ADD COLUMN verified_owner_name VARCHAR(100) NULL;
