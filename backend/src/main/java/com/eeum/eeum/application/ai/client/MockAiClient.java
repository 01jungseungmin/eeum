package com.eeum.eeum.application.ai.client;

import org.springframework.stereotype.Component;

/**
 * 발표 당일 Gemini/Groq 장애 대비용 Mock 클라이언트.
 * API Key 없이 실제 AI처럼 보이는 고정 한국어 응답을 반환한다. 외부 호출 없음.
 */
@Component
public class MockAiClient implements AiClient {

    @Override
    public AiClientResponse generate(AiClientRequest request) {
        return new AiClientResponse(AiProviderType.MOCK, "mock", answerFor(request.taskType()));
    }

    private String answerFor(AiTaskType taskType) {
        return switch (taskType) {
            case PRODUCT_DESCRIPTION ->
                    "매일 아침 준비하는 신선한 재료로 정성껏 만들었습니다. 담백한 국물과 넉넉한 구성으로 든든한 한 끼를 즐기실 수 있어요.";
            case EVENT_MARKETING_COPY -> """
                    {"title":"이번 주 점심 이벤트 안내 🍽️","content":"이번 주 평일 점심(11:00~14:00)에 방문하시면 대표 메뉴를 특별 가격에 만나보실 수 있어요. 준비 수량이 소진되면 조기 마감될 수 있으니 여유 있게 방문해 주세요."}""";
            case STORE_NOTICE_DRAFT -> """
                    {"title":"운영 안내 드립니다","content":"안녕하세요, 사장님 가게를 찾아주시는 고객님들께 안내드립니다. 이번 주도 정상 영업하며, 신메뉴가 준비되어 있습니다. 방문 전 참고 부탁드립니다. 감사합니다."}""";
            case OWNER_REPLY_DRAFT ->
                    "소중한 후기 남겨주셔서 진심으로 감사합니다. 말씀해주신 부분은 꼼꼼히 확인해 더 나은 모습으로 보답하겠습니다. 다음 방문 때도 좋은 경험 드릴 수 있도록 노력하겠습니다.";
            case POLICY_CLASSIFICATION -> "NORMAL";
            case OUTPUT_REVIEW -> "PASS";
            case CHATBOT_REPLY ->
                    "네, 사장님. 요청하신 내용을 확인했어요. 리뷰 답글, 문의 답변, 공지 문구 작성을 바로 도와드릴 수 있습니다. 아래 버튼에서 원하시는 작업을 선택해 주세요.";
            case INSIGHT_SUMMARY ->
                    "최근 지표를 종합하면 단골 고객의 반응이 꾸준히 유지되고 있습니다. 반응이 좋았던 시간대를 중심으로 다음 이벤트를 준비해보시는 것을 추천드려요.";
        };
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.MOCK;
    }
}
