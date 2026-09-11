import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import {
  ArrowLeft,
  TrendingUp,
  Sparkles,
  CheckCircle2,
  Lightbulb,
  Tag,
  Heart,
  AlertCircle,
} from 'lucide-react';

export default function AiEventDetailPage() {
  const navigate = useNavigate();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchEventPerformance = async () => {
      try {
        setLoading(true);
        const response = await aiManagerApi.getEventPerformance();

        const result = response.data;
        if (result.success) {
          setData(result.data);
        } else {
          setError(result.message || '데이터를 불러오지 못했습니다.');
        }
      } catch (err) {
        console.error('이벤트 성과 조회 실패:', err);
        setError('서버 통신 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchEventPerformance();
  }, []);

  const handleGoToCreateEvent = () => {
    navigate('/ai-manager/event/create', {
      state: {
        recommendation: data?.nextEventRecommendation || null,
      },
    });
  };

  if (loading) {
    return (
      <PageWrapper>
        <StatusText>데이터를 불러오는 중입니다...</StatusText>
      </PageWrapper>
    );
  }

  if (error) {
    return (
      <PageWrapper>
        <StatusText $isError>{error}</StatusText>
      </PageWrapper>
    );
  }

  // hasData 가 false 여도 아래 전체 UI를 그대로 렌더링합니다.
  const hasData = data?.hasData ?? false;

  return (
    <PageWrapper>
      {/* 뒤로가기 버튼 */}
      <BackButton onClick={() => navigate(-1)}>
        <ArrowLeft size={18} />
        <span>AI 매니저로 돌아가기</span>
      </BackButton>

      {/* 상단 페이지 헤더 */}
      <HeaderSection>
        <HeaderIconBox>
          <TrendingUp
            size={24}
            color="#16a34a"
          />
        </HeaderIconBox>
        <HeaderTitleBox>
          <TitleRow>
            <h1>이벤트 성과 상세</h1>
            <AiBadge>
              <Sparkles size={12} /> AI 분석 완료
            </AiBadge>
          </TitleRow>
          <HeaderSubText>
            지난 이벤트의 조회수·전환율·신규·단골 반응을 상세 분석해요.
          </HeaderSubText>
        </HeaderTitleBox>
      </HeaderSection>

      {/* 데이터 미충분 시 상단 안내 배너 */}
      {!hasData && (
        <NoticeBanner>
          <AlertCircle
            size={18}
            color="#0284c7"
          />
          <span>
            {data?.emptyMessage ||
              '이벤트 기간 주문 데이터가 아직 충분하지 않습니다.'}
          </span>
        </NoticeBanner>
      )}

      {/* 메인 2열 그리드 레이아웃 */}
      <ContentGrid>
        {/* 좌측 영역 */}
        <LeftColumn>
          {/* 1. 이벤트 성과 지표 */}
          <Card>
            <CardHeader>
              <h2>이벤트 성과 지표</h2>
              <p>최근 이벤트 성과 분석</p>
            </CardHeader>
            <MetricsGrid>
              <MetricBox>
                <MetricLabel>상품 조회수</MetricLabel>
                <MetricValueGroup>
                  <NewValue>
                    {data?.productViewCount?.toLocaleString() ?? 0}{' '}
                    <span>회</span>
                  </NewValue>
                </MetricValueGroup>
              </MetricBox>

              <MetricBox>
                <MetricLabel>이벤트 주문 수</MetricLabel>
                <MetricValueGroup>
                  <NewValue>
                    {data?.eventOrderCount?.toLocaleString() ?? 0}{' '}
                    <span>건</span>
                  </NewValue>
                </MetricValueGroup>
              </MetricBox>

              <MetricBox>
                <MetricLabel>주문 전환율</MetricLabel>
                <MetricValueGroup>
                  <NewValue>
                    {data?.orderConversionRate != null
                      ? (data.orderConversionRate * 100).toFixed(1)
                      : 0}{' '}
                    <span>%</span>
                  </NewValue>
                </MetricValueGroup>
              </MetricBox>

              <MetricBox>
                <MetricLabel>신규 고객 비중</MetricLabel>
                <MetricValueGroup>
                  <NewValue>
                    {data?.newCustomerRatio != null
                      ? (data.newCustomerRatio * 100).toFixed(0)
                      : 0}{' '}
                    <span>%</span>
                  </NewValue>
                </MetricValueGroup>
              </MetricBox>
            </MetricsGrid>
          </Card>

          {/* 2. AI 요약 */}
          <Card>
            <CardHeader>
              <h2>AI 요약</h2>
            </CardHeader>
            <AiSummaryCard>
              <AiSummaryIcon>
                <Sparkles
                  size={18}
                  color="#16a34a"
                />
              </AiSummaryIcon>
              <AiSummaryText>
                <strong>AI 요약</strong>
                <p>
                  {data?.aiSummary ||
                    '아직 이벤트 성과 데이터가 충분하지 않습니다.'}
                </p>
              </AiSummaryText>
            </AiSummaryCard>
          </Card>

          {/* 3. 추천 액션 */}
          <Card>
            <CardHeader>
              <h2>추천 액션</h2>
              <p>다음 이벤트에 반영하면 좋아요</p>
            </CardHeader>
            {data?.nextEventRecommendation ? (
              <>
                <ActionList>
                  {data.nextEventRecommendation.recommendedProductName && (
                    <ActionItem>
                      <CheckCircle2
                        size={18}
                        color="#16a34a"
                      />
                      <span>
                        추천 메뉴:{' '}
                        <strong>
                          {data.nextEventRecommendation.recommendedProductName}
                        </strong>
                      </span>
                    </ActionItem>
                  )}
                  {data.nextEventRecommendation.recommendedTimeRange && (
                    <ActionItem>
                      <CheckCircle2
                        size={18}
                        color="#16a34a"
                      />
                      <span>
                        추천 시간대(
                        {data.nextEventRecommendation.recommendedTimeRange})
                        집중 노출로 전환율을 더 끌어올릴 수 있어요.
                      </span>
                    </ActionItem>
                  )}
                </ActionList>

                {data.nextEventRecommendation.reason && (
                  <TipBox>
                    <Lightbulb
                      size={18}
                      color="#d97706"
                    />
                    <span>{data.nextEventRecommendation.reason}</span>
                  </TipBox>
                )}
              </>
            ) : (
              <NoRecommendationText>
                추가 데이터 수집 후 맞춤 추천이 제공됩니다.
              </NoRecommendationText>
            )}
          </Card>
        </LeftColumn>

        {/* 우측 영역 */}
        <RightColumn>
          {/* 주요 지표 요약 카드 */}
          <RightCard>
            <SummaryTitle>주요 지표 요약</SummaryTitle>
            <SummaryList>
              <SummaryRow>
                <span>조회수</span>
                <NormalText>{data?.productViewCount ?? 0}회</NormalText>
              </SummaryRow>
              <SummaryRow>
                <span>주문 전환율</span>
                <HighlightGreen>
                  +
                  {data?.orderConversionRate != null
                    ? (data.orderConversionRate * 100).toFixed(1)
                    : 0}
                  %
                </HighlightGreen>
              </SummaryRow>
              <SummaryRow>
                <span>신규 고객 비중</span>
                <HighlightGreen>
                  {data?.newCustomerRatio != null
                    ? (data.newCustomerRatio * 100).toFixed(0)
                    : 0}
                  %
                </HighlightGreen>
              </SummaryRow>
              <SummaryRow>
                <span>단골 재주문</span>
                <NormalText>{data?.regularReorderCount ?? 0}건</NormalText>
              </SummaryRow>
            </SummaryList>
          </RightCard>

          {/* 우측 하단 액션 버튼 그룹 */}
          <ButtonGroup>
            <PrimaryButton onClick={handleGoToCreateEvent}>
              <Tag size={18} /> 다음 이벤트 만들기
            </PrimaryButton>
            <SecondaryButton onClick={() => navigate('/messages/create')}>
              <Heart size={18} /> 단골 메시지 보내기
            </SecondaryButton>
          </ButtonGroup>
        </RightColumn>
      </ContentGrid>
    </PageWrapper>
  );
}

// --- Styled Components ---

const PageWrapper = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
  background-color: #f8fafc;
  min-height: 100vh;
  box-sizing: border-box;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
`;

const StatusText = styled.div`
  text-align: center;
  padding: 100px 0;
  color: ${(props) => (props.$isError ? '#ef4444' : '#64748b')};
  font-size: 15px;
`;

const NoticeBanner = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 12px;
  padding: 14px 18px;
  margin-bottom: 24px;
  color: #0369a1;
  font-size: 14px;
`;

const BackButton = styled.button`
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  margin-bottom: 24px;

  &:hover {
    color: #1e293b;
  }
`;

const HeaderSection = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
`;

const HeaderIconBox = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background-color: #dcfce7;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const HeaderTitleBox = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const TitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;

  h1 {
    font-size: 24px;
    font-weight: 700;
    color: #0f172a;
    margin: 0;
  }
`;

const AiBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #dcfce7;
  color: #15803d;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
`;

const HeaderSubText = styled.p`
  font-size: 14px;
  color: #64748b;
  margin: 0;
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;

  @media (max-width: 992px) {
    grid-template-columns: 1fr;
  }
`;

const LeftColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

const Card = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid #f1f5f9;
`;

const CardHeader = styled.div`
  margin-bottom: 20px;

  h2 {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 4px 0;
  }

  p {
    font-size: 13px;
    color: #94a3b8;
    margin: 0;
  }
`;

const MetricsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
`;

const MetricBox = styled.div`
  background-color: #f8fafc;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #f1f5f9;
`;

const MetricLabel = styled.div`
  font-size: 13px;
  color: #64748b;
  margin-bottom: 12px;
`;

const MetricValueGroup = styled.div`
  display: flex;
  align-items: baseline;
  gap: 8px;
`;

const NewValue = styled.span`
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;

  span {
    font-size: 16px;
    font-weight: 500;
    margin-left: 2px;
  }
`;

const AiSummaryCard = styled.div`
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
`;

const AiSummaryIcon = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background-color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
`;

const AiSummaryText = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    font-size: 13px;
    color: #166534;
  }

  p {
    font-size: 14px;
    color: #15803d;
    margin: 0;
  }
`;

const ActionList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
`;

const ActionItem = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #334155;

  strong {
    color: #0f172a;
  }
`;

const TipBox = styled.div`
  background-color: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #92400e;
`;

const NoRecommendationText = styled.p`
  font-size: 14px;
  color: #94a3b8;
  margin: 0;
`;

const RightColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const RightCard = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid #f1f5f9;
`;

const SummaryTitle = styled.h3`
  font-size: 14px;
  font-weight: 600;
  color: #94a3b8;
  margin: 0 0 16px 0;
`;

const SummaryList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SummaryRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #64748b;
`;

const HighlightGreen = styled.span`
  font-weight: 700;
  color: #16a34a;
`;

const NormalText = styled.span`
  font-weight: 700;
  color: #0f172a;
`;

const ButtonGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const PrimaryButton = styled.button`
  width: 100%;
  background-color: #41b37d;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  padding: 14px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #369a6a;
  }
`;

const SecondaryButton = styled.button`
  width: 100%;
  background-color: #ffffff;
  color: #166534;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #f8fafc;
  }
`;
