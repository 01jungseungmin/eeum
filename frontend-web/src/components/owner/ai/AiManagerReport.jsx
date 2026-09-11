import React from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { Sparkles, ArrowRight, CheckCircle2 } from 'lucide-react';

const CardContainer = styled.div`
  background: linear-gradient(135deg, #134e35 0%, #1a5d42 100%);
  border-radius: 16px;
  padding: 28px 32px;
  color: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
`;

const ContentLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 65%;

  @media (max-width: 768px) {
    max-width: 100%;
  }
`;

const Badge = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(4px);
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #a3e635;
  width: fit-content;
`;

const Title = styled.h2`
  font-size: 24px;
  font-weight: 700;
  margin: 0;
  line-height: 1.35;
  color: #ffffff;
`;

const Description = styled.p`
  font-size: 13px;
  color: #a3ccbe;
  margin: 0;
  line-height: 1.5;
`;

const AskAiButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(4px);
  padding: 10px 16px;
  border-radius: 10px;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  width: fit-content;
  margin-top: 8px;
  gap: 16px;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.25);
    transform: translateY(-1px);
  }
`;

const ButtonLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const FooterNote = styled.span`
  font-size: 11px;
  color: #6b9e89;
  margin-top: 4px;
`;

const CountBox = styled.div`
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  padding: 20px 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 160px;
  position: relative;
  overflow: hidden;

  @media (max-width: 768px) {
    width: 100%;
    box-sizing: border-box;
  }
`;

const CountTitle = styled.span`
  font-size: 12px;
  color: #a3ccbe;
  margin-bottom: 4px;
  z-index: 1;
`;

const CountNumber = styled.span`
  font-size: 32px;
  font-weight: 800;
  color: #ffffff;
  margin-bottom: 4px;
  z-index: 1;

  span {
    font-size: 18px;
    font-weight: 600;
    margin-left: 2px;
  }
`;

const CountSub = styled.span`
  font-size: 11px;
  color: #81c784;
  z-index: 1;
`;

const CheckIconBg = styled(CheckCircle2)`
  position: absolute;
  right: -10px;
  bottom: -10px;
  color: rgba(255, 255, 255, 0.08);
`;

// 날짜 포맷 함수 (예: 2026-08-31T17:21:04 -> "오늘 17:21 기준")
const formatReportTime = (isoString) => {
  if (!isoString) return '오늘 기준';
  try {
    const date = new Date(isoString);
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `오늘 ${hours}:${minutes} 기준`;
  } catch {
    return '오늘 기준';
  }
};

export default function AiManagerReport({
  todoCount,
  reportedAt,
  privacyNotice,
}) {
  const navigate = useNavigate();

  const formattedTime = formatReportTime(reportedAt);

  return (
    <CardContainer id="section-ai-report">
      <ContentLeft>
        <Badge>
          <Sparkles size={14} /> AI 점장의 영업 전 보고
        </Badge>
        <Title>사장님, 영업 전 확인할 내용을 정리해뒀어요.</Title>
        <Description>
          확인 후 전송·등록만 해주세요. AI는 임의로 발송하지 않습니다.
          <br />
          {formattedTime}으로 정리된 영업 전 보고입니다.
        </Description>

        {/* AI 점장에게 직접 물어보기 버튼 */}
        <AskAiButton onClick={() => navigate('/ai-manager/chat')}>
          <ButtonLeft>
            <Sparkles
              size={18}
              color="#ffffff"
            />
            <span>AI 점장에게 직접 물어보기</span>
          </ButtonLeft>
          <ArrowRight
            size={18}
            color="#ffffff"
          />
        </AskAiButton>

        <FooterNote>
          ⓘ{' '}
          {privacyNotice ||
            '고객 동의 범위 내에서 제공되는 신호 바탕으로 작성됐습니다.'}
        </FooterNote>
      </ContentLeft>

      <CountBox>
        <CheckIconBg size={80} />
        <CountTitle>오늘 처리할 항목</CountTitle>
        <CountNumber>
          {todoCount ?? 0}
          <span>건</span>
        </CountNumber>
        <CountSub>검토 후 전송·등록 대기 중</CountSub>
      </CountBox>
    </CardContainer>
  );
}
