package com.eeum.eeum.application.owner.enums;

public enum OwnerCustomerType {
    ALL,
    POTENTIAL,  // 주문 완료 이력 없음, 찜 또는 채팅 참여 이력 있음
    NEW,        // 완료 주문 1회
    NORMAL,     // 완료 주문 2회 이상
    REGULAR     // 최근 6개월 내 완료 주문 3회 이상
}
