package com.eeum.eeum.domain.used.enums;

// 중고 게시글의 거래 유형
public enum UsedProductPriceType {
    FIXED,       // 정가 — price > 0
    FREE,        // 나눔 — price == 0
    NEGOTIABLE   // 가격 제안 — price == null
}
