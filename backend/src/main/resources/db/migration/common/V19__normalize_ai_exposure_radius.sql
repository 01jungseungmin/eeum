-- 조건 변경 API는 반경 1 / 1.5 / 3km만 허용한다(AiLocalMatchConditionRequestDto).
-- 허용값 밖으로 저장된 행은 화면이 그 값을 되돌려 보내 조건 변경이 400으로 실패하므로 가장 가까운 허용값으로 맞춘다.
UPDATE ai_exposure_status
SET radius_km   = CASE
                      WHEN radius_km < 1.25 THEN 1.0
                      WHEN radius_km < 2.25 THEN 1.5
                      ELSE 3.0
                  END,
    modified_at = NOW(6)
WHERE radius_km NOT IN (1.0, 1.5, 3.0);
