-- 기존 중복은 가장 작은 account_id만 보존한다. 이후 제약으로 재발을 막는다.
UPDATE account a
JOIN (
    SELECT fcm_token, MIN(account_id) AS keeper_id
    FROM account
    WHERE fcm_token IS NOT NULL
    GROUP BY fcm_token
    HAVING COUNT(*) > 1
) duplicate ON duplicate.fcm_token = a.fcm_token
SET a.fcm_token = NULL
WHERE a.account_id <> duplicate.keeper_id;

-- 한 기기 토큰은 한 계정만 소유한다. NULL은 여러 행에 허용돼 로그아웃·탈퇴 계정에는 영향이 없다.
ALTER TABLE account
    ADD CONSTRAINT uk_account_fcm_token UNIQUE (fcm_token);
