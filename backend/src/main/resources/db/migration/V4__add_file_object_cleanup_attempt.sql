-- 정리 실패 건이 무한 재시도되며 claimExpiredUnattached의 top-100 앞자리를 계속 차지해
-- 뒤의 정리 작업이 굶는 문제를 막는다. 시도 횟수를 세고, 한계를 넘기면 CLEANUP_FAILED로
-- 내려 스케줄러 대상에서 제외한다.
--
-- ENUM 값은 알파벳순으로 적는다 — Hibernate가 @Enumerated(STRING)을 그 순서로 생성하므로
-- 순서가 어긋나면 ddl-auto=validate로 만든 스키마와 비교가 흐트러진다.
ALTER TABLE file_object
    MODIFY COLUMN status ENUM('ATTACHED','CLEANUP_FAILED','CLEANUP_PENDING','CONFIRMED') NOT NULL;

-- 기존 행은 아직 한 번도 실패하지 않은 것으로 본다.
ALTER TABLE file_object
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status;
