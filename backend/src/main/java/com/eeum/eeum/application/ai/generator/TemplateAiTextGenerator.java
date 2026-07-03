package com.eeum.eeum.application.ai.generator;

import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiNoticeType;
import com.eeum.eeum.domain.ai.enums.AiTone;
import org.springframework.stereotype.Component;

// 1차 MVP: 실제 LLM 호출 없이 템플릿 기반으로 문구 생성
@Component
public class TemplateAiTextGenerator implements AiTextGenerator {

    @Override
    public AiText customerCareMessage(AiCareType careType, String storeName, String contextHint) {
        String hint = (contextHint == null || contextHint.isBlank()) ? "인기 메뉴" : contextHint;
        return switch (careType) {
            case CART_INTEREST -> AiText.of(
                    "담아두신 상품 안내",
                    "담아두신 " + hint + "가(이) 준비되어 있습니다. 필요하실 때 편하게 이용해보세요.");
            case INACTIVE_REGULAR -> AiText.of(
                    "오랜만이에요",
                    "오랜만이에요. 자주 찾아주셨던 " + hint + "가(이) 이번 주 다시 준비되었습니다.");
            case INQUIRY_HESITATION -> AiText.of(
                    "문의하신 내용 안내",
                    "남겨주신 문의 관련해 안내드려요. 궁금하신 점 있으시면 편하게 말씀해주세요.");
        };
    }

    @Override
    public AiText reviewReply(String storeName, int rating, String reviewContent) {
        if (rating >= 4) {
            return AiText.content("소중한 리뷰 감사합니다. 앞으로도 " + storeName + "에서 좋은 경험 드릴 수 있도록 노력하겠습니다.");
        }
        if (rating == 3) {
            return AiText.content("리뷰 남겨주셔서 감사합니다. 말씀해주신 부분은 꼼꼼히 확인해 더 나은 모습으로 보답하겠습니다.");
        }
        return AiText.content("불편을 드려 정말 죄송합니다. 말씀해주신 내용을 바로 확인하고 개선하겠습니다. 다시 방문해주시면 좋은 경험으로 보답하겠습니다.");
    }

    @Override
    public AiText inquiryReply(String storeName, String inquiryTitle) {
        return AiText.content("문의해주셔서 감사합니다. '" + inquiryTitle + "' 관련해 안내드립니다. 추가로 궁금하신 점이 있으시면 편하게 말씀해주세요.");
    }

    @Override
    public AiText complaintReply(String storeName, String keyword) {
        return AiText.content("최근 '" + keyword + "' 관련 말씀을 여러 번 들었습니다. 불편을 드려 죄송하며, 즉시 개선 조치를 진행하고 있습니다. 소중한 의견 감사합니다.");
    }

    @Override
    public AiText marketingCopy(String storeName, AiNoticeType noticeType, AiTone tone, String keyword) {
        String subject = (keyword == null || keyword.isBlank()) ? storeName : keyword;
        String body = switch (noticeType) {
            case EVENT -> switch (tone) {
                case POLITE -> subject + " 이벤트를 준비했습니다. 이번 기회에 꼭 들러주세요.";
                case FRIENDLY -> subject + " 이벤트 시작! 놓치면 아쉬워요. 지금 확인해보세요!";
                case SHORT -> subject + " 이벤트 진행 중.";
            };
            case TEMP_CLOSED -> switch (tone) {
                case POLITE -> "죄송합니다. " + storeName + "이(가) 잠시 휴무합니다. 재오픈 시 다시 안내드리겠습니다.";
                case FRIENDLY -> storeName + " 잠깐 쉬어가요! 곧 다시 만나요.";
                case SHORT -> storeName + " 임시 휴무 안내.";
            };
            case NEW_MENU -> switch (tone) {
                case POLITE -> storeName + "에 새로운 메뉴가 준비되었습니다. 많은 관심 부탁드립니다.";
                case FRIENDLY -> "신메뉴 나왔어요! " + storeName + "에서 제일 먼저 맛보세요.";
                case SHORT -> storeName + " 신메뉴 출시.";
            };
        };
        return AiText.of("[" + storeName + "] " + noticeType.getDisplayName(), body);
    }

    @Override
    public AiText noticeCopy(String storeName, AiNoticeType noticeType, AiTone tone, String keyword) {
        return marketingCopy(storeName, noticeType, tone, keyword);
    }
}
