package com.eeum.eeum.domain.chat.enums;

public enum MessageType {
    TEXT,    // 텍스트 메시지
    IMAGE,   // 이미지 메시지
    SYSTEM,  // 시스템 메시지 (입장/퇴장 등)
    LOCATION // 위치 메시지 (거래 장소 제안 — 좌표·장소명 필수)
}
