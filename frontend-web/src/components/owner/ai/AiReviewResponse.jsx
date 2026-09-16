import styled from 'styled-components';
import {
  Star,
  ChevronRight,
  AlertTriangle,
  MessageSquare,
  MessageCircle,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

// data: dashboardData?.reviewInquirySummary (ReviewInquirySummaryDto)
// { unansweredReviewCount, unansweredInquiryCount, complaintKeywordCount }
export default function AiReviewResponse({ data }) {
  const navigate = useNavigate();
  const d = data || {};
  const unansweredReviewCount = d.unansweredReviewCount ?? 0;
  const unansweredInquiryCount = d.unansweredInquiryCount ?? 0;
  const complaintKeywordCount = d.complaintKeywordCount ?? 0;

  return (
    <CardContainer id="section-ai-review">
      <Header>
        <HeaderLeft>
          <IconBox>
            <Star size={20} />
          </IconBox>
          <TitleArea>
            <h3>리뷰·문의 자동 대응</h3>
            <p>답글 초안 · 반복 불만 감지</p>
          </TitleArea>
        </HeaderLeft>

        <MoreButton onClick={() => navigate('/ai-manager/review')}>
          더보기 <ChevronRight size={16} />
        </MoreButton>
      </Header>

      {/* 1. 반복 불만 경고 박스 (실제 반복 불만 키워드가 있을 때만 노출) */}
      {complaintKeywordCount > 0 && (
        <WarningBox>
          <WarningHeader>
            <AlertTriangle size={16} /> 최근 2주 반복 불만 키워드가{' '}
            {complaintKeywordCount}건 감지됐어요.
          </WarningHeader>
          <WarningSub>자세한 키워드와 대응 문구는 상세 페이지에서 확인하세요.</WarningSub>
          <WarningButtonGroup>
            <ActionBtn
              $primary
              onClick={() => navigate('/ai-manager/review')}
            >
              <MessageSquare size={14} /> 답글 초안 만들기
            </ActionBtn>
          </WarningButtonGroup>
        </WarningBox>
      )}

      {/* 2. 미답변 리뷰 */}
      <ItemRow>
        <ItemLeft>
          <ItemIconBox
            $bgColor="#fef9c3"
            $color="#ca8a04"
          >
            <Star size={18} />
          </ItemIconBox>
          <ItemText>
            <h4>미답변 리뷰</h4>
            <p>{unansweredReviewCount}건 · 답글을 기다리고 있어요</p>
          </ItemText>
        </ItemLeft>
        <OutlineBtn onClick={() => navigate('/ai-manager/review')}>
          답글 초안 보기
        </OutlineBtn>
      </ItemRow>

      {/* 3. 미답변 문의 */}
      <ItemRow>
        <ItemLeft>
          <ItemIconBox
            $bgColor="#e0f2fe"
            $color="#0284c7"
          >
            <MessageCircle size={18} />
          </ItemIconBox>
          <ItemText>
            <h4>미답변 문의</h4>
            <p>{unansweredInquiryCount}건</p>
          </ItemText>
        </ItemLeft>
        <OutlineBtn onClick={() => navigate('/ai-manager/review')}>
          답변 초안 보기
        </OutlineBtn>
      </ItemRow>
    </CardContainer>
  );
}

// Styled-Components 생략 (기존 스타일 코드 동일 유지)
// 스타일 선언 (이전 동일)
const CardContainer = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;
const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;
const HeaderLeft = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
`;
const IconBox = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: #fef9c3;
  color: #ca8a04;
  display: flex;
  align-items: center;
  justify-content: center;
`;
const TitleArea = styled.div`
  h3 {
    font-size: 18px;
    font-weight: 700;
    margin: 0;
    color: #111827;
  }
  p {
    font-size: 12px;
    color: #6b7280;
    margin: 4px 0 0;
  }
`;
const MoreButton = styled.button`
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
`;
const WarningBox = styled.div`
  background-color: #fdf2f2;
  border: 1px solid #fde8e8;
  border-radius: 12px;
  padding: 16px;
`;
const WarningHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e02424;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
`;
const WarningSub = styled.p`
  font-size: 12px;
  color: #9b1c1c;
  margin: 0 0 12px 0;
`;
const WarningButtonGroup = styled.div`
  display: flex;
  gap: 10px;
`;
const ActionBtn = styled.button`
  flex: 1;
  background-color: #ffffff;
  border: 1px solid ${(props) => (props.$primary ? '#bbf7d0' : '#e5e7eb')};
  color: ${(props) => (props.$primary ? '#166534' : '#374151')};
  padding: 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
`;
const ItemRow = styled.div`
  background-color: #fafafa;
  border: 1px solid #f3f4f6;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
`;
const ItemLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;
const ItemIconBox = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background-color: ${(props) => props.$bgColor || '#f3f4f6'};
  color: ${(props) => props.$color || '#374151'};
  display: flex;
  align-items: center;
  justify-content: center;
`;
const ItemText = styled.div`
  h4 {
    font-size: 14px;
    font-weight: 700;
    color: #1f2937;
    margin: 0;
  }
  p {
    font-size: 12px;
    color: #6b7280;
    margin: 2px 0 0;
  }
`;
const OutlineBtn = styled.button`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  color: #374151;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
`;
