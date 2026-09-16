package com.eeum.eeum.domain.inquiry.event;

import com.eeum.eeum.domain.inquiry.enums.InquiryTargetType;

// 신규 문의 접수 시 발행. STORE → 해당 사장, ADMIN → 관리자 전체에게 알림
public record InquirySubmittedEvent(
        Long inquiryId,
        InquiryTargetType targetType,
        Long storeOwnerAccountId,   // STORE 문의면 스토어 오너 ID, ADMIN 문의면 null
        String writerNickname,
        String inquiryTitle
) {}
