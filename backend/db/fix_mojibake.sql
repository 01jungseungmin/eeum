-- =============================================
-- 더미 데이터 한글 깨짐(이중 인코딩) 복구
--   dummy.sql 이 latin1 커넥션으로 적재되어 "김치찌개" -> "ê¹€ì¹˜ì°Œê°œ" 로 저장됨.
--   UTF-8 바이트를 latin1로 되돌린 뒤 다시 utf8mb4로 해석해 복원.
--
--   각 UPDATE 는 "복원하면 한글이 되고, 지금은 한글이 아닌" 행만 건드리므로
--   멱등하며 정상 데이터(상점 20~35 등)에는 영향 없음.
-- =============================================

UPDATE `account`           SET `name`               = CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `name`               NOT REGEXP '[가-힣]';
UPDATE `account`           SET `nickname`           = CONVERT(BINARY(CONVERT(`nickname`           USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`nickname`           USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `nickname`           NOT REGEXP '[가-힣]';
UPDATE `owner_info`        SET `rejection_reason`   = CONVERT(BINARY(CONVERT(`rejection_reason`   USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`rejection_reason`   USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `rejection_reason`   NOT REGEXP '[가-힣]';
UPDATE `product`           SET `name`               = CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `name`               NOT REGEXP '[가-힣]';
UPDATE `product`           SET `description`        = CONVERT(BINARY(CONVERT(`description`        USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`description`        USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `description`        NOT REGEXP '[가-힣]';
UPDATE `product_category`  SET `name`               = CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `name`               NOT REGEXP '[가-힣]';
UPDATE `product_image`     SET `image_url`          = CONVERT(BINARY(CONVERT(`image_url`          USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`image_url`          USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `image_url`          NOT REGEXP '[가-힣]';
UPDATE `region`            SET `si_do`              = CONVERT(BINARY(CONVERT(`si_do`              USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`si_do`              USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `si_do`              NOT REGEXP '[가-힣]';
UPDATE `region`            SET `gun_gu`             = CONVERT(BINARY(CONVERT(`gun_gu`             USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`gun_gu`             USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `gun_gu`             NOT REGEXP '[가-힣]';
UPDATE `region`            SET `dong`               = CONVERT(BINARY(CONVERT(`dong`               USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`dong`               USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `dong`               NOT REGEXP '[가-힣]';
UPDATE `settlement_account` SET `bank_name`         = CONVERT(BINARY(CONVERT(`bank_name`          USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`bank_name`          USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `bank_name`          NOT REGEXP '[가-힣]';
UPDATE `settlement_account` SET `account_holder`    = CONVERT(BINARY(CONVERT(`account_holder`     USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`account_holder`     USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `account_holder`     NOT REGEXP '[가-힣]';
UPDATE `store`             SET `name`               = CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`name`               USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `name`               NOT REGEXP '[가-힣]';
UPDATE `store`             SET `address`            = CONVERT(BINARY(CONVERT(`address`            USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`address`            USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `address`            NOT REGEXP '[가-힣]';
UPDATE `store`             SET `description`        = CONVERT(BINARY(CONVERT(`description`        USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`description`        USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `description`        NOT REGEXP '[가-힣]';
UPDATE `store_notice`      SET `title`              = CONVERT(BINARY(CONVERT(`title`              USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`title`              USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `title`              NOT REGEXP '[가-힣]';
UPDATE `store_notice`      SET `content`            = CONVERT(BINARY(CONVERT(`content`            USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`content`            USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `content`            NOT REGEXP '[가-힣]';
UPDATE `used_product`      SET `title`              = CONVERT(BINARY(CONVERT(`title`              USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`title`              USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `title`              NOT REGEXP '[가-힣]';
UPDATE `used_product`      SET `content`            = CONVERT(BINARY(CONVERT(`content`            USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`content`            USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `content`            NOT REGEXP '[가-힣]';
UPDATE `used_product`      SET `trade_location_name`= CONVERT(BINARY(CONVERT(`trade_location_name`USING latin1)) USING utf8mb4) WHERE CONVERT(BINARY(CONVERT(`trade_location_name`USING latin1)) USING utf8mb4) REGEXP '[가-힣]' AND `trade_location_name`NOT REGEXP '[가-힣]';
