-- 정리 실패 건이 무한 재시도되며 claimExpiredUnattached의 top-100 앞자리를 계속 차지해
-- 뒤의 정리 작업이 굶는 문제를 막는다. 시도 횟수를 세고, 한계를 넘기면 CLEANUP_FAILED로
-- 내려 스케줄러 대상에서 제외한다.
--
-- 새 ENUM 값은 목록 맨 끝에 붙인다. 중간에 끼우면 뒤 멤버의 내부 번호가 밀려 MySQL이
-- ALGORITHM=INPLACE로 처리하지 못하고 테이블을 통째로 복사하며, 그동안 쓰기가 막힌다.
ALTER TABLE file_object
    MODIFY COLUMN status ENUM('ATTACHED','CLEANUP_PENDING','CONFIRMED','CLEANUP_FAILED') NOT NULL;

-- 기존 행은 아직 한 번도 실패하지 않은 것으로 본다.
ALTER TABLE file_object
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status;
