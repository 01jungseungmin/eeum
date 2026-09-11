import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  ArrowLeft,
  Star,
  Sparkles,
  AlertTriangle,
  MessageSquare,
  MessageCircle,
  Megaphone,
  Info,
} from 'lucide-react';

// 모달 컴포넌트 (경로에 맞게 수정해 주세요)
import ComplaintReplyModal from '../../../components/owner/ai/modal/ComplaintReplyModal';

export default function AiReviewDetailNoticePage() {
  const navigate = useNavigate();

  // 모달 상태 관리
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedReviewId, setSelectedReviewId] = useState(null);

  const handleOpenModal = (reviewId = 1) => {
    setSelectedReviewId(reviewId);
    setIsModalOpen(true);
  };

  return (
    <PageWrapper>
      <Container>
        {/* 상단 뒤로가기 */}
        <BackButton onClick={() => navigate(-1)}>
          <ArrowLeft size={18} /> AI 매니저로 돌아가기
        </BackButton>

        {/* 헤더 영역 */}
        <HeaderRow>
          <HeaderIconBox>
            <Star size={24} />
          </HeaderIconBox>
          <HeaderTitleGroup>
            <TitleRow>
              <h1>리뷰·문의 대응 상세</h1>
              <Badge>
                <Sparkles size={13} /> AI 분석 완료
              </Badge>
            </TitleRow>
            <p>미답변 리뷰·문의 현황과 반복 불만 키워드를 모아서 보여줘요.</p>
          </HeaderTitleGroup>
        </HeaderRow>

        {/* 메인 2열 그리드 레이아웃 */}
        <MainGrid>
          {/* 좌측 영역 */}
          <LeftColumn>
            {/* 경고 배너 */}
            <WarningBanner>
              <WarningTitle>
                <AlertTriangle size={16} /> 최근 2주 리뷰에서 '대기 시간' 표현이
                5회 반복됐어요.
              </WarningTitle>
              <WarningSub>
                별점은 유지 중이지만, 불만이 쌓이고 있습니다.
              </WarningSub>
            </WarningBanner>

            {/* 미답변 현황 카드 */}
            <Card>
              <CardHeader>
                <h3>미답변 현황</h3>
                <p>답글·답변을 기다리는 항목</p>
              </CardHeader>
              <ItemList>
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
                      <p>3건 · 답글을 기다리고 있어요</p>
                    </ItemText>
                  </ItemLeft>
                  <OutlineBtn onClick={() => handleOpenModal(1)}>
                    답글 초안 보기
                  </OutlineBtn>
                </ItemRow>

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
                      <p>2건 · 주차, 영업시간 관련</p>
                    </ItemText>
                  </ItemLeft>
                  <OutlineBtn>답변 초안 보기</OutlineBtn>
                </ItemRow>
              </ItemList>
            </Card>

            {/* 반복 불만 키워드 카드 */}
            <Card>
              <CardHeaderBetween>
                <CardHeader>
                  <h3>반복 불만 키워드</h3>
                  <p>최근 리뷰·신고에서 반복 감지된 신호</p>
                </CardHeader>
                <CautionBadge>
                  <AlertTriangle size={12} /> 주의
                </CautionBadge>
              </CardHeaderBetween>

              <KeywordList>
                <KeywordItem>
                  <KeywordLeft>
                    <KeywordTag
                      $color="#e11d48"
                      $bgColor="#ffe4e6"
                    >
                      대기 시간
                    </KeywordTag>
                    <KeywordInfo>
                      <strong>리뷰 5건</strong>
                      <span>2주 연속 감지</span>
                    </KeywordInfo>
                  </KeywordLeft>
                  <KeywordCount $color="#e11d48">5건</KeywordCount>
                </KeywordItem>

                <KeywordItem>
                  <KeywordLeft>
                    <KeywordTag
                      $color="#e11d48"
                      $bgColor="#ffe4e6"
                    >
                      위생
                    </KeywordTag>
                    <KeywordInfo>
                      <strong>리뷰 3 · 신고 1</strong>
                      <span>2주 연속 감지</span>
                    </KeywordInfo>
                  </KeywordLeft>
                  <KeywordCount $color="#e11d48">4건</KeywordCount>
                </KeywordItem>

                <AdviceBox>
                  <Info size={16} />
                  <span>
                    '대기 시간' 관련 불만이 쌓이고 있어요. 조리 동선 점검과 안내
                    공지를 권장해요.
                  </span>
                </AdviceBox>
              </KeywordList>
              <FooterCaption>출처: 이음 리뷰·문의·신고 데이터</FooterCaption>
            </Card>
          </LeftColumn>

          {/* 우측 응대 현황 요약 및 버튼 카드 */}
          <RightColumn>
            <SummaryCard>
              <SummaryTitle>응대 현황</SummaryTitle>
              <SummaryList>
                <SummaryItem>
                  <span>미답변 리뷰</span>
                  <CountText $color="#ca8a04">3건</CountText>
                </SummaryItem>
                <SummaryItem>
                  <span>미답변 문의</span>
                  <CountText $color="#0284c7">2건</CountText>
                </SummaryItem>
                <SummaryItem>
                  <span>반복 불만 키워드</span>
                  <CountText $color="#e11d48">2건</CountText>
                </SummaryItem>
              </SummaryList>
            </SummaryCard>

            <ActionButtons>
              <PrimaryBtn onClick={() => handleOpenModal(1)}>
                <MessageSquare size={16} /> 답글 초안 만들기
              </PrimaryBtn>
              <SecondaryBtn onClick={() => navigate('/ai-manager/notice')}>
                <Megaphone size={16} /> 공지 문구 만들기
              </SecondaryBtn>
            </ActionButtons>
          </RightColumn>
        </MainGrid>
      </Container>

      {/* 답글 초안 모달 */}
      <ComplaintReplyModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        reviewId={selectedReviewId}
        topic="대기 시간"
      />
    </PageWrapper>
  );
}

// --- Styled Components ---
const PageWrapper = styled.div`
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 32px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const Container = styled.div`
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const BackButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  color: #4b5563;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;

  &:hover {
    color: #111827;
  }
`;

const HeaderRow = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const HeaderIconBox = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background-color: #fef3c7;
  color: #d97706;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const HeaderTitleGroup = styled.div`
  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: #6b7280;
  }
`;

const TitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;

  h1 {
    font-size: 22px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
`;

const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #dcfce7;
  color: #15803d;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 20px;
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const LeftColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const RightColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 24px;
`;

const WarningBanner = styled.div`
  background-color: #fff1f2;
  border: 1px solid #ffe4e6;
  border-radius: 16px;
  padding: 16px 20px;
`;

const WarningTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e11d48;
  font-size: 14px;
  font-weight: 700;
`;

const WarningSub = styled.p`
  margin: 4px 0 0;
  font-size: 12px;
  color: #be123c;
`;

const Card = styled.div`
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CardHeader = styled.div`
  h3 {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  p {
    font-size: 12px;
    color: #9ca3af;
    margin: 3px 0 0;
  }
`;

const CardHeaderBetween = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const CautionBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #ffe4e6;
  color: #e11d48;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 20px;
`;

const ItemList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const ItemRow = styled.div`
  background-color: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 14px;
  padding: 14px 18px;
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
  width: 38px;
  height: 38px;
  border-radius: 10px;
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
    color: #9ca3af;
    margin: 2px 0 0;
  }
`;

const OutlineBtn = styled.button`
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #334155;
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background-color: #f8fafc;
  }
`;

const KeywordList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const KeywordItem = styled.div`
  background-color: #fff1f2;
  border: 1px solid #ffe4e6;
  border-radius: 14px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const KeywordLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;
`;

const KeywordTag = styled.div`
  background-color: ${(props) => props.$bgColor || '#e11d48'};
  color: ${(props) => props.$color || '#ffffff'};
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
`;

const KeywordInfo = styled.div`
  display: flex;
  flex-direction: column;
  strong {
    font-size: 13px;
    color: #111827;
  }
  span {
    font-size: 11px;
    color: #9ca3af;
    margin-top: 1px;
  }
`;

const KeywordCount = styled.span`
  font-size: 14px;
  font-weight: 700;
  color: ${(props) => props.$color || '#111827'};
`;

const AdviceBox = styled.div`
  background-color: #fff1f2;
  border: 1px solid #ffe4e6;
  border-radius: 12px;
  padding: 12px 16px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #e11d48;
  font-size: 12px;
  line-height: 1.5;
`;

const FooterCaption = styled.span`
  font-size: 11px;
  color: #9ca3af;
  margin-top: -4px;
`;

const SummaryCard = styled.div`
  background: #ffffff;
  border-radius: 20px;
  padding: 20px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
`;

const SummaryTitle = styled.h4`
  font-size: 13px;
  font-weight: 600;
  color: #9ca3af;
  margin: 0 0 14px 0;
`;

const SummaryList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const SummaryItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #4b5563;
`;

const CountText = styled.span`
  font-weight: 700;
  color: ${(props) => props.$color || '#111827'};
`;

const ActionButtons = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const PrimaryBtn = styled.button`
  width: 100%;
  background-color: #41b37d;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  padding: 14px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;

  &:hover {
    background-color: #369a6a;
  }
`;

const SecondaryBtn = styled.button`
  width: 100%;
  background-color: #ffffff;
  border: 1px solid #e2e8f0;
  color: #374151;
  border-radius: 12px;
  padding: 14px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;

  &:hover {
    background-color: #f8fafc;
  }
`;
