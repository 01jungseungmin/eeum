import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Users, Sparkles, Send, Megaphone } from 'lucide-react';
import CustomerCareCard from '../../../components/owner/ai/CustomerCareCard';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import { CARE_TYPE_CONFIG } from '../../../constants/aiManager'; // CARE_TYPE_CONFIG import 필요

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  padding-bottom: 40px;
`;

const BackButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  width: fit-content;

  &:hover {
    color: #111827;
  }
`;

const HeaderArea = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const HeaderIconBox = styled.div`
  width: 48px;
  height: 48px;
  background-color: #e0e7ff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4f46e5;
  flex-shrink: 0;
`;

const HeaderTitleGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;

    h1 {
      font-size: 22px;
      font-weight: 800;
      color: #111827;
      margin: 0;
    }
  }

  p {
    font-size: 13px;
    color: #6b7280;
    margin: 0;
  }
`;

const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #ecfdf5;
  color: #059669;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 12px;
  border: 1px solid #a7f3d0;
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const CardListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SidePanel = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const SummaryCard = styled.div`
  background-color: #ffffff;
  border: 1px solid #f3f4f6;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);

  h3 {
    font-size: 13px;
    color: #6b7280;
    margin: 0 0 16px 0;
    font-weight: 600;
  }
`;

const SummaryList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const SummaryRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;

  .label {
    color: #4b5563;
  }

  .value {
    font-weight: 700;
    color: #111827;
  }
`;

const ActionButtonGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const PrimaryButton = styled.button`
  width: 100%;
  padding: 14px;
  background-color: #34d399;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #10b981;
  }
`;

const SecondaryButton = styled.button`
  width: 100%;
  padding: 14px;
  background-color: #ffffff;
  color: #374151;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background-color: #f9fafb;
    border-color: #d1d5db;
  }
`;

const FooterNote = styled.p`
  font-size: 12px;
  color: #9ca3af;
  margin: 12px 0 0 0;
`;

const LoadingText = styled.div`
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
`;

export default function AiCareDetailPage() {
  const navigate = useNavigate();

  // API 상태 관리
  const [careList, setCareList] = useState([]);
  const [summaryData, setSummaryData] = useState(null);
  const [loading, setLoading] = useState(true);

  // 모달 제어
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [modalErrorMessage, setModalErrorMessage] = useState('');

  useEffect(() => {
    const fetchPageData = async () => {
      try {
        setLoading(true);

        // 조회할 careType 목록 정의
        const careTypes = [
          'CART_INTEREST',
          'INACTIVE_REGULAR',
          'INQUIRY_HESITATION',
        ];

        // 3가지 케어 카드 단건 조회와 활동 요약 조회를 동시에 요청
        const [careResults, summaryRes] = await Promise.allSettled([
          Promise.all(
            careTypes.map((type) =>
              aiManagerApi.getCustomerCareCardByType(type),
            ),
          ),
        ]);

        // 1. 케어 카드 데이터 설정
        if (careResults.status === 'fulfilled') {
          // Promise.all로 묶인 응답에서 data.data만 추출
          const cardsData = careResults.value
            .filter((res) => res?.data?.success)
            .map((res) => res.data.data);

          setCareList(cardsData);
        }

        // 2. 활동 요약 데이터 설정
        if (
          summaryRes.status === 'fulfilled' &&
          summaryRes.value?.data?.success
        ) {
          setSummaryData(summaryRes.value.data.data);
        } else if (summaryRes.status === 'rejected') {
          const errResponse = summaryRes.reason?.response?.data;
          if (errResponse?.error?.code === 'AI_001') {
            setModalErrorMessage(errResponse.error.message);
            setIsUpgradeModalOpen(true);
          }
        }
      } catch (error) {
        console.error('페이지 데이터 로딩 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPageData();
  }, []);

  const excludedCount = careList[0]?.recentlyNotifiedExcludedCount ?? 0;

  return (
    <PageContainer>
      <BackButton onClick={() => navigate('/ai-manager')}>
        <ArrowLeft size={16} /> AI 매니저로 돌아가기
      </BackButton>

      <HeaderArea>
        <HeaderIconBox>
          <Users size={24} />
        </HeaderIconBox>
        <HeaderTitleGroup>
          <div className="title-row">
            <h1>AI 고객 케어 상세</h1>
            <Badge>
              <Sparkles size={12} /> AI 분석 완료
            </Badge>
          </div>
          <p>다시 안내하면 좋을 고객 분석과 AI 준비 메시지 현황을 보여줘요.</p>
        </HeaderTitleGroup>
      </HeaderArea>

      <MainGrid>
        {/* 좌측 카드 리스트 (API 연동 데이터) */}
        <CardListContainer>
          {loading ? (
            <LoadingText>
              고객 케어 상세 분석을 불러오는 중입니다...
            </LoadingText>
          ) : (
            careList.map((item, index) => {
              const config =
                CARE_TYPE_CONFIG[item.careType] ||
                CARE_TYPE_CONFIG.CART_INTEREST;
              const cardId = item.careType || `care-card-${index}`;

              const formattedData = {
                id: cardId,
                careType: item.careType,
                icon: config.icon,
                iconBgColor: config.iconBgColor,
                iconColor: config.iconColor,
                priority: item.priority
                  ? `우선순위 ${item.priority}`
                  : `우선순위 ${index + 1}`,
                title: `${item.title || config.defaultTitle} ${item.targetCustomerCount ?? 0}명`,
                description: item.reason || config.defaultDesc,
                message: item.preparedMessage || null,
                sendable: item.sendable ?? false,
                secondaryAction:
                  item.careType === 'INQUIRY_HESITATION' ||
                  config.secondaryAction,
                targetCustomerCount: item.targetCustomerCount ?? 0,
              };

              return (
                <CustomerCareCard
                  key={cardId}
                  data={formattedData}
                  variant="detail"
                />
              );
            })
          )}
        </CardListContainer>

        {/* 우측 사이드 패널 */}
        <SidePanel>
          <SummaryCard>
            <h3>{summaryData?.period || '최근 30일'} 전송 현황</h3>
            <SummaryList>
              <SummaryRow>
                <span className="label">전송 완료</span>
                <span className="value">
                  {loading ? '-' : `${summaryData?.sentCount ?? 0}건`}
                </span>
              </SummaryRow>
              <SummaryRow>
                <span className="label">대기 중 초안</span>
                <span className="value">
                  {loading ? '-' : `${summaryData?.draftCount ?? 0}건`}
                </span>
              </SummaryRow>
              <SummaryRow>
                <span className="label">단골 안부 메시지</span>
                <span className="value">
                  {loading ? '-' : `${summaryData?.regularMessageCount ?? 0}건`}
                </span>
              </SummaryRow>

              <SummaryRow>
                <span className="label">발송 후 재방문</span>
                <span className="value">
                  {loading
                    ? '-'
                    : `${summaryData?.revisitAfterMessageCount ?? 0}명`}
                </span>
              </SummaryRow>
            </SummaryList>
          </SummaryCard>

          <ActionButtonGroup>
            <PrimaryButton onClick={() => navigate('/ai-manager/chat')}>
              <Send size={16} /> AI 점장에게 물어보기
            </PrimaryButton>
            <SecondaryButton onClick={() => navigate('/ai-manager/notice')}>
              <Megaphone size={16} /> 공지 문구 만들기
            </SecondaryButton>
          </ActionButtonGroup>
        </SidePanel>
      </MainGrid>

      <FooterNote>
        ⓘ 최근 7일 내 알림을 받은 고객 {excludedCount}명은 피로도 방지 차원에서
        자동 제외됐습니다. 상세 행동 로그는 노출하지 않고, 요약된 관계 신호만
        제공합니다.
      </FooterNote>

      {/* 플랜 업그레이드 안내 모달 */}
      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => setIsUpgradeModalOpen(false)}
        errorMessage={modalErrorMessage}
      />
    </PageContainer>
  );
}
