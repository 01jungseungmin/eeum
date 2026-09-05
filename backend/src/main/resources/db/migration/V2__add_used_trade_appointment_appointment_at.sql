-- 기존 확정 장소에는 약속 시간이 없었으므로, 기존 행은 마지막 수정 시각을 임시값으로 쓴다.
-- 이후 애플리케이션은 appointment_at을 항상 명시해 저장한다.
ALTER TABLE used_trade_appointment
    ADD COLUMN appointment_at DATETIME(6) NULL AFTER place_id;

UPDATE used_trade_appointment
SET appointment_at = modified_at
WHERE appointment_at IS NULL;

ALTER TABLE used_trade_appointment
    MODIFY COLUMN appointment_at DATETIME(6) NOT NULL;
