package com.eeum.eeum.domain.chat.enums;

// 채팅방이 연관된 도메인 타입 (Polymorphic 참조, FK 아님)
public enum ChatRoomRefType {
    USED_PRODUCT,  // 중고거래
    STORE,      // 상점 단체 채팅
    COMMUNITY,  // 커뮤니티
    NONE        // 일반 채팅 (특정 도메인 미연관)
}