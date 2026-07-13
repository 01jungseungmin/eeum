package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;

/**
 * 고객·마케팅 메시지 등 "발송용 문구" 생성 인터페이스.
 * 1차 MVP는 Template 구현체 사용 — 추후 OpenAI 등 실제 LLM 구현체로 교체한다.
 * Controller/Service에서 LLM API를 직접 호출하지 않고 반드시 이 인터페이스를 경유한다.
 */
public interface AiTextGenerator {

    AiText customerCareMessage(AiCareType careType, String storeName, String contextHint);

    AiText reviewReply(String storeName, int rating, String reviewContent);

    AiText inquiryReply(String storeName, String inquiryTitle);

    AiText complaintReply(String storeName, String keyword);

    AiText marketingCopy(String storeName, AiNoticeType noticeType, AiTone tone, String keyword);

    AiText noticeCopy(String storeName, AiNoticeType noticeType, AiTone tone, String keyword);
}
