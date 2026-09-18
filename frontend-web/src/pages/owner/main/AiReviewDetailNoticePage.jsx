import { useEffect, useState } from 'react';
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

import ComplaintReplyModal from '../../../components/owner/ai/modal/ComplaintReplyModal';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';

// 상대 시간 포맷 (NotificationItem.jsx의 formatTimeAgo와 동일한 규칙)
const formatTimeAgo = (dateString) => {
  if (!dateString) return '';
  const diffMinutes = Math.floor((new Date() - new Date(dateString)) / 60000);

  if (diffMinutes < 1) return '방금 전';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;

  return `${Math.floor(diffHours / 24)}일 전`;
};

export default function AiReviewDetailNoticePage() {
  const navigate = useNavigate();

  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(true);

  // 모달 상태 관리
  const [modalState, setModalState] = useState({
    isOpen: false,
    type: 'review',
    targetId: null,
    keyword: undefined,
    subtitle: undefined,
  });

  const fetchOverview = async () => {
    try {
      setLoading(true);
      const response = await aiManagerApi.getReviewInquiryStatus();
      if (response.data?.success) {
        setOverview(response.data.data);
      }
    } catch (error) {
      console.error('리뷰/문의 자동 대응 현황 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    queueMicrotask(() => fetchOverview());
  }, []);

  const openReviewModal = (review) => {
    setModalState({
      isOpen: true,
      type: 'review',
      targetId: review.reviewId,
      keyword: undefined,
      subtitle: `별점 ${review.rating}점 · ${formatTimeAgo(review.createdAt)}`,
    });
  };

  const openInquiryModal = (inquiry) => {
    setModalState({
      isOpen: true,
      type: 'inquiry',
      targetId: inquiry.inquiryId,
      keyword: undefined,
      subtitle: inquiry.title,
    });
  };

  const openComplaintModal = (keyword) => {
    setModalState({
      isOpen: true,
      type: 'complaint',
      targetId: null,
      keyword,
      subtitle: `'${keyword}' 관련`,
    });
  };

  const closeModal = () => setModalState((prev) => ({ ...prev, isOpen: false }));

  const unansweredReviews = overview?.unansweredReviews || [];
  const unansweredInquiries = overview?.unansweredInquiries || [];
  const complaintKeywords = overview?.complaintKeywords || [];

  if (loading) {
    return (
      <PageWrapper>
        <Container>
          <EmptyText>불러오는 중...</EmptyText>
        </Container>
      </PageWrapper>
    );
  }

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
            {/* 경고 배너 (반복 불만 키워드가 있을 때만) */}
            {complaintKeywords.length > 0 && (
              <WarningBanner>
                <WarningTitle>
                  <AlertTriangle size={16} /> 최근 2주 반복 불만 키워드가{' '}
                  {complaintKeywords.length}건 감지됐어요.
                </WarningTitle>
                {overview?.recommendedResponse && (
                  <WarningSub>{overview.recommendedResponse}</WarningSub>
                )}
              </WarningBanner>
            )}

            {/* 미답변 현황 카드 */}
            <Card>
              <CardHeader>
                <h3>미답변 현황</h3>
                <p>답글·답변을 기다리는 항목</p>
              </CardHeader>
              {unansweredReviews.length === 0 &&
              unansweredInquiries.length === 0 ? (
                <EmptyText>
                  {overview?.emptyMessage || '미답변 항목이 없습니다.'}
                </EmptyText>
              ) : (
                <ItemList>
                  {unansweredReviews.map((review) => (
                    <ItemRow key={`review-${review.reviewId}`}>
                      <ItemLeft>
                        <ItemIconBox
                          $bgColor="#fef9c3"
                          $color="#ca8a04"
                        >
                          <Star size={18} />
                        </ItemIconBox>
                        <ItemText>
                          <h4>미답변 리뷰 · 별점 {review.rating}점</h4>
                          <p>
                            {review.content} · {formatTimeAgo(review.createdAt)}
                          </p>
                        </ItemText>
                      </ItemLeft>
                      <OutlineBtn onClick={() => openReviewModal(review)}>
                        답글 초안 보기
                      </OutlineBtn>
                    </ItemRow>
                  ))}

                  {unansweredInquiries.map((inquiry) => (
                    <ItemRow key={`inquiry-${inquiry.inquiryId}`}>
                      <ItemLeft>
                        <ItemIconBox
                          $bgColor="#e0f2fe"
                          $color="#0284c7"
                        >
                          <MessageCircle size={18} />
                        </ItemIconBox>
                        <ItemText>
                          <h4>미답변 문의</h4>
                          <p>
                            {inquiry.title} · {formatTimeAgo(inquiry.createdAt)}
                          </p>
                        </ItemText>
                      </ItemLeft>
                      <OutlineBtn onClick={() => openInquiryModal(inquiry)}>
                        답변 초안 보기
                      </OutlineBtn>
                    </ItemRow>
                  ))}
                </ItemList>
              )}
            </Card>

            {/* 반복 불만 키워드 카드 */}
            <Card>
              <CardHeaderBetween>
                <CardHeader>
                  <h3>반복 불만 키워드</h3>
                  <p>최근 리뷰·문의에서 반복 감지된 신호</p>
                </CardHeader>
                {complaintKeywords.length > 0 && (
                  <CautionBadge>
                    <AlertTriangle size={12} /> 주의
                  </CautionBadge>
                )}
              </CardHeaderBetween>

              {complaintKeywords.length === 0 ? (
                <EmptyText>감지된 반복 불만 키워드가 없습니다.</EmptyText>
              ) : (
                <KeywordList>
                  {complaintKeywords.map((keyword) => (
                    <KeywordItem key={keyword}>
                      <KeywordLeft>
                        <KeywordTag
                          $color="#e11d48"
                          $bgColor="#ffe4e6"
                        >
                          {keyword}
                        </KeywordTag>
                      </KeywordLeft>
                      <OutlineBtn onClick={() => openComplaintModal(keyword)}>
                        답글 초안 만들기
                      </OutlineBtn>
                    </KeywordItem>
                  ))}

                  {overview?.recommendedResponse && (
                    <AdviceBox>
                      <Info size={16} />
                      <span>{overview.recommendedResponse}</span>
                    </AdviceBox>
                  )}
                </KeywordList>
              )}
              <FooterCaption>출처: 이음 리뷰·문의 데이터</FooterCaption>
            </Card>
          </LeftColumn>

          {/* 우측 응대 현황 요약 및 버튼 카드 */}
          <RightColumn>
            <SummaryCard>
              <SummaryTitle>응대 현황</SummaryTitle>
              <SummaryList>
                <SummaryItem>
                  <span>미답변 리뷰</span>
                  <CountText $color="#ca8a04">
                    {overview?.unansweredReviewCount ?? 0}건
                  </CountText>
                </SummaryItem>
                <SummaryItem>
                  <span>미답변 문의</span>
                  <CountText $color="#0284c7">
                    {overview?.unansweredInquiryCount ?? 0}건
                  </CountText>
                </SummaryItem>
                <SummaryItem>
                  <span>반복 불만 키워드</span>
                  <CountText $color="#e11d48">
                    {complaintKeywords.length}건
                  </CountText>
                </SummaryItem>
              </SummaryList>
            </SummaryCard>

            <ActionButtons>
              <PrimaryBtn
                disabled={
                  unansweredReviews.length === 0 &&
                  complaintKeywords.length === 0
                }
                onClick={() => {
                  if (unansweredReviews.length > 0) {
                    openReviewModal(unansweredReviews[0]);
                  } else if (complaintKeywords.length > 0) {
                    openComplaintModal(complaintKeywords[0]);
                  }
                }}
              >
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
        isOpen={modalState.isOpen}
        onClose={closeModal}
        type={modalState.type}
        targetId={modalState.targetId}
        keyword={modalState.keyword}
        subtitle={modalState.subtitle}
        onSuccess={fetchOverview}
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

const EmptyText = styled.div`
  text-align: center;
  padding: 30px 0;
  color: #9ca3af;
  font-size: 13px;
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

  &:disabled {
    background-color: #cbd5e1;
    cursor: not-allowed;
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
