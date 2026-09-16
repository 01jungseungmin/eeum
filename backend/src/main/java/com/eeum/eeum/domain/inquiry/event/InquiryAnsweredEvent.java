package com.eeum.eeum.domain.inquiry.event;

public record InquiryAnsweredEvent(
        Long inquiryId,
        Long writerAccountId,
        String inquiryTitle
) {}
