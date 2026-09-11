import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import {
  ArrowLeft,
  MapPin,
  Sparkles,
  Users,
  Sliders,
  CheckCircle2,
  X,
  ChevronDown,
  Check,
  Radio,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';

const PageContainer = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
  background-color: #f8fafc;
  min-height: 100vh;
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
  margin-bottom: 20px;
  padding: 0;

  &:hover {
    color: #1e293b;
  }
`;

const PageHeader = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
`;

const HeaderIconBox = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background-color: #e0e7ff;
  color: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const TitleWrapper = styled.div`
  .title-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 4px;

    h1 {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }
  }

  p {
    font-size: 14px;
    color: #64748b;
    margin: 0;
  }
`;

const AiBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #ecfdf5;
  color: #059669;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid #a7f3d0;
`;

const ActiveExposureBanner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 24px;

  .left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .dot {
    width: 10px;
    height: 10px;
    background-color: #10b981;
    border-radius: 50%;
  }

  .text-box {
    .title {
      font-size: 15px;
      font-weight: 700;
      color: #166534;
      margin-bottom: 2px;
    }
    .sub {
      font-size: 13px;
      color: #15803d;
    }
  }
`;

const StopButton = styled.button`
  background-color: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;

  &:hover {
    background-color: #f8fafc;
  }
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const LeftColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const RightColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const Card = styled.div`
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
`;

const CardHeader = styled.div`
  margin-bottom: 20px;

  .card-title-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  h3 {
    font-size: 16px;
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

const ScoreDetailsGrid = styled.div`
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 32px;
  align-items: center;

  @media (max-width: 600px) {
    grid-template-columns: 1fr;
  }
`;

const DonutContainer = styled.div`
  position: relative;
  width: 130px;
  height: 130px;
  margin: 0 auto;
`;

const DonutGraphic = styled.div`
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: ${({ $score }) =>
    `conic-gradient(#10b981 0% ${$score}%, #e2e8f0 ${$score}% 100%)`};
  display: flex;
  align-items: center;
  justify-content: center;

  &::after {
    content: '';
    width: 96px;
    height: 96px;
    background-color: #ffffff;
    border-radius: 50%;
  }
`;

const DonutScoreText = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;

  span {
    font-size: 14px;
    font-weight: 600;
  }
`;

const ProgressBarList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ProgressItem = styled.div`
  .bar-info {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    margin-bottom: 6px;

    .label {
      color: #334155;
      font-weight: 500;
    }
    .value {
      color: #0f172a;
      font-weight: 700;
    }
  }
`;

const Track = styled.div`
  width: 100%;
  height: 8px;
  background-color: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
`;

const Fill = styled.div`
  height: 100%;
  width: ${({ value }) => value}%;
  background-color: ${({ color }) => color || '#10b981'};
  border-radius: 4px;
  transition: width 0.5s ease-in-out;
`;

const TotalBadge = styled.span`
  background-color: #eff6ff;
  color: #3b82f6;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 12px;
`;

const SegmentList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const SegmentItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background-color: #f8fafc;
  border-radius: 12px;

  .left {
    display: flex;
    align-items: center;
    gap: 12px;
    color: #475569;
    font-size: 14px;
    font-weight: 600;
  }

  .right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .count {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }
`;

const TagTypeBadge = styled.span`
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: ${({ type }) =>
    type === '핵심' ? '#e0e7ff' : type === '추천' ? '#e0f2fe' : '#f1f5f9'};
  color: ${({ type }) =>
    type === '핵심' ? '#4338ca' : type === '추천' ? '#0369a1' : '#475569'};
`;

const TagGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
`;

const KeywordTag = styled.span`
  background-color: #e0e7ff;
  color: #4338ca;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 8px;
`;

const ReasonList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ReasonItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
  color: #334155;
  line-height: 1.5;

  strong {
    color: #0f172a;
    font-weight: 700;
  }
`;

const RightChartCard = styled(Card)`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 24px;
  text-align: center;

  .card-label {
    align-self: flex-start;
    font-size: 13px;
    color: #64748b;
    font-weight: 500;
    margin-bottom: 24px;
  }

  .percentile-text {
    margin-top: 16px;
    font-size: 13px;
    color: #64748b;
    font-weight: 500;
  }
`;

const PrimaryButton = styled.button`
  width: 100%;
  background-color: #10b981;
  color: #ffffff;
  border: none;
  padding: 16px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #059669;
  }
`;

const ActiveStatusButton = styled.button`
  width: 100%;
  background-color: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
  padding: 16px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #d1fae5;
  }
`;

const SecondaryButton = styled.button`
  width: 100%;
  background-color: #ffffff;
  color: #334155;
  border: 1px solid #cbd5e1;
  padding: 14px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;

  &:hover {
    background-color: #f8fafc;
  }
`;

const FooterText = styled.p`
  font-size: 12px;
  color: #94a3b8;
  margin-top: 24px;
`;

// Drawer Components
const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 1000;
  opacity: ${({ $isVisible }) => ($isVisible ? 1 : 0)};
  visibility: ${({ $isVisible }) => ($isVisible ? 'visible' : 'hidden')};
  transition:
    opacity 0.3s ease,
    visibility 0.3s ease;
`;

const Drawer = styled.div`
  position: fixed;
  top: 0;
  right: 0;
  width: 380px;
  max-width: 100%;
  height: 100%;
  background-color: #ffffff;
  z-index: 1001;
  transform: ${({ $isVisible }) =>
    $isVisible ? 'translateX(0)' : 'translateX(100%)'};
  transition: transform 0.3s ease-in-out;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.08);
`;

const DrawerHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  border-bottom: 1px solid #f1f5f9;

  h2 {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
    margin: 0;
  }
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;

  &:hover {
    background-color: #f1f5f9;
    color: #334155;
  }
`;

const DrawerContent = styled.div`
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 28px;
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;

  label {
    font-size: 14px;
    font-weight: 700;
    color: #0f172a;
  }

  .sub-desc {
    font-size: 12px;
    color: #94a3b8;
    margin-top: 2px;
  }
`;

const SegmentedControl = styled.div`
  display: flex;
  background-color: #f1f5f9;
  padding: 4px;
  border-radius: 10px;
  gap: 4px;
`;

const SegmentOption = styled.button`
  flex: 1;
  padding: 10px 0;
  font-size: 13px;
  font-weight: 600;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background-color: ${({ $active }) => ($active ? '#ffffff' : 'transparent')};
  color: ${({ $active }) => ($active ? '#0f172a' : '#64748b')};
  box-shadow: ${({ $active }) =>
    $active ? '0 1px 3px rgba(0, 0, 0, 0.1)' : 'none'};
  transition: all 0.2s;
`;

const CustomSelectWrapper = styled.div`
  position: relative;
`;

const SelectTrigger = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #334155;
  cursor: pointer;
  background-color: #ffffff;
  transition: border-color 0.2s;

  &:hover {
    border-color: #10b981;
  }
`;

const DropdownMenu = styled.div`
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  width: 100%;
  background: #5a5a5a;
  border-radius: 10px;
  padding: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const DropdownItem = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 14px;
  color: #ffffff;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;

  &:hover {
    background-color: rgba(255, 255, 255, 0.1);
  }

  .check-icon {
    width: 14px;
    height: 14px;
    visibility: ${({ $selected }) => ($selected ? 'visible' : 'hidden')};
  }
`;

const ResultBanner = styled.div`
  background-color: #ecfdf5;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .label {
    font-size: 13px;
    color: #065f46;
    font-weight: 500;
  }

  .count {
    font-size: 16px;
    font-weight: 800;
    color: #047857;
  }
`;

const DrawerFooter = styled.div`
  padding: 20px 24px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  gap: 12px;
`;

const CancelButton = styled.button`
  padding: 14px 20px;
  background-color: transparent;
  color: #64748b;
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    color: #0f172a;
  }
`;

const ApplyButton = styled.button`
  flex: 1;
  padding: 14px;
  background-color: #10b981;
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;

  &:hover {
    background-color: #059669;
  }
`;

// 날짜 문구 포맷팅 헬퍼 함수
const formatStartTime = (isoString) => {
  if (!isoString) return '';
  const dateObj = new Date(isoString);
  const now = new Date();

  const isToday = dateObj.toDateString() === now.toDateString();
  const hours = String(dateObj.getHours()).padStart(2, '0');
  const minutes = String(dateObj.getMinutes()).padStart(2, '0');

  if (isToday) {
    return `오늘 ${hours}:${minutes}`;
  }
  const month = dateObj.getMonth() + 1;
  const day = dateObj.getDate();
  return `${month}/${day} ${hours}:${minutes}`;
};

// ==================== [ 메인 컴포넌트 ] ====================

export default function AiLocationMatchingDetailPage() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  // 노출 상태 관련 State
  const [isExposing, setIsExposing] = useState(false);
  const [exposureStartTime, setExposureStartTime] = useState('');

  // 모달 및 드롭다운 상태
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSelectOpen, setIsSelectOpen] = useState(false);

  // 플랜 업그레이드 모달 상태
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [upgradeErrorMessage, setUpgradeErrorMessage] = useState('');

  // 조건 관리 상태
  const [radiusKm, setRadiusKm] = useState(1.5);
  const [interest, setInterest] = useState('한식');
  const [customerType, setCustomerType] = useState('ALL');

  const interestOptions = [
    { label: '한식 관심 고객', value: '한식' },
    { label: '점심 수요 고객', value: '점심' },
    { label: '전체 관심사', value: '전체' },
  ];

  const customerTypeMap = {
    ALL: '전체',
    REGULAR: '단골',
    NEW: '신규',
  };

  const reverseCustomerTypeMap = {
    전체: 'ALL',
    단골: 'REGULAR',
    신규: 'NEW',
  };

  // 노출 상태 별도 조회 함수
  const fetchExposureStatus = useCallback(async () => {
    try {
      const exposureRes = await aiManagerApi.getExposureStatus();
      if (exposureRes.data && exposureRes.data.success) {
        const expData = exposureRes.data.data;

        // active 상태 및 stoppedAt 여부 판단
        const isActive = expData.active && !expData.stoppedAt;
        setIsExposing(isActive);

        if (expData.startedAt) {
          setExposureStartTime(formatStartTime(expData.startedAt));
        }

        // 받아온 최신 조건을 UI 상태와 동기화
        if (expData.radiusKm) setRadiusKm(expData.radiusKm);
        if (expData.interest) setInterest(expData.interest);
        if (expData.customerType) setCustomerType(expData.customerType);
      }
    } catch (error) {
      console.error('노출 상태 조회 실패:', error);
    }
  }, []);

  // 페이지 데이터 초기 로드
  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        setLoading(true);

        const [detailRes] = await Promise.all([
          aiManagerApi.getLocalMatchAnalysis(),
          fetchExposureStatus(),
        ]);

        if (detailRes.data && detailRes.data.success) {
          const apiData = detailRes.data.data;
          setData(apiData);
          if (apiData.radiusKm) setRadiusKm(apiData.radiusKm);
          if (apiData.interest) setInterest(apiData.interest);
          if (apiData.customerType) setCustomerType(apiData.customerType);
        }
      } catch (error) {
        console.error('초기 데이터 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchInitialData();
  }, [fetchExposureStatus]);

  // PATCH: 조건 변경 API
  const updateConditions = async (newRadius, newInterest, newCustomerType) => {
    try {
      const payload = {
        radiusKm: newRadius,
        interest: newInterest,
        customerType: newCustomerType,
      };

      const response = await aiManagerApi.updateLocalMatchConditions(payload);
      if (response.data && response.data.success) {
        setData(response.data.data);
        await fetchExposureStatus(); // 조건 변경 후 노출 정보 동기화
      }
      alert('조건이 성공적으로 변경되었습니다.');
    } catch (error) {
      console.error('조건 변경 실패:', error);
    }
  };

  // POST: 노출 시작 API
  const handleStartExposure = async () => {
    try {
      const response = await aiManagerApi.startExposure();
      if (response.data && response.data.success) {
        await fetchExposureStatus(); // 시작 후 노출 상태 재조회
      }
    } catch (error) {
      console.error('노출 시작 실패:', error);

      const status = error.response?.status;
      const errorData = error.response?.data?.error || error.response?.data;
      const errorCode = errorData?.code;
      const errorMessage = errorData?.message;

      if (status === 403 || errorCode === 'AI_001') {
        setUpgradeErrorMessage(
          errorMessage ||
            '현재 플랜에서 사용할 수 없는 기능입니다. 플랜 업그레이드가 필요합니다.',
        );
        setIsUpgradeModalOpen(true);
      }
    }
  };

  // POST: 노출 중지 API
  const handleStopExposure = async () => {
    try {
      const response = await aiManagerApi.stopExposure();
      if (response.data && response.data.success) {
        await fetchExposureStatus(); // 중지 후 노출 상태 재조회
      }
    } catch (error) {
      console.error('노출 중지 실패:', error);
    }
  };

  const handleRadiusChange = (val) => {
    const numericRadius = parseFloat(val);
    setRadiusKm(numericRadius);
  };

  const handleInterestSelect = (val) => {
    setInterest(val);
    setIsSelectOpen(false);
  };

  const handleCustomerTypeChange = (typeLabel) => {
    const mappedType = reverseCustomerTypeMap[typeLabel] || 'ALL';
    setCustomerType(mappedType);
  };

  const handleApplyConditions = () => {
    updateConditions(radiusKm, interest, customerType);
    setIsModalOpen(false);
  };

  if (loading)
    return <PageContainer>데이터를 불러오는 중입니다...</PageContainer>;
  if (!data) return <PageContainer>데이터가 없습니다.</PageContainer>;

  const {
    totalScore = 92,
    regionMatchRate = 96,
    interestMatchRate = 91,
    eventFitScore = 88,
    regularCustomerRatio = 84,
    segments = [],
    estimatedTargetCount = 3459,
    interest: currentInterest = '한식',
  } = data;

  const currentInterestLabel =
    interestOptions.find((opt) => opt.value === interest)?.label ||
    `${interest} 관심 고객`;

  return (
    <PageContainer>
      <BackButton onClick={() => navigate(-1)}>
        <ArrowLeft size={16} /> AI 매니저로 돌아가기
      </BackButton>

      <PageHeader>
        <HeaderIconBox>
          <MapPin size={24} />
        </HeaderIconBox>
        <TitleWrapper>
          <div className="title-row">
            <h1>생활권 매칭 매니저</h1>
            <AiBadge>
              <Sparkles size={12} /> AI 분석 완료
            </AiBadge>
          </div>
          <p>
            우리 가게와 잘 맞는 주변 생활권 고객을 찾아 노출 대상을 추천해요.
          </p>
        </TitleWrapper>
      </PageHeader>

      {isExposing && (
        <ActiveExposureBanner>
          <div className="left">
            <div className="dot" />
            <div className="text-box">
              <div className="title">노출 진행 중</div>
              <div className="sub">
                {estimatedTargetCount.toLocaleString()}명 생활권 고객에게
                노출되고 있어요 · {exposureStartTime} 시작
              </div>
            </div>
          </div>
          <StopButton onClick={handleStopExposure}>중지</StopButton>
        </ActiveExposureBanner>
      )}

      <ContentGrid>
        <LeftColumn>
          <Card>
            <CardHeader>
              <h3>매칭 점수 구성</h3>
              <p>우리 가게가 주변 생활권과 얼마나 잘 맞는지</p>
            </CardHeader>
            <ScoreDetailsGrid>
              <DonutContainer>
                <DonutGraphic $score={totalScore} />
                <DonutScoreText>
                  {totalScore}
                  <span>점</span>
                </DonutScoreText>
              </DonutContainer>

              <ProgressBarList>
                <ProgressItem>
                  <div className="bar-info">
                    <span className="label">지역 일치 (서울 마포구)</span>
                    <span className="value">{regionMatchRate}%</span>
                  </div>
                  <Track>
                    <Fill
                      value={regionMatchRate}
                      color="#10b981"
                    />
                  </Track>
                </ProgressItem>

                <ProgressItem>
                  <div className="bar-info">
                    <span className="label">
                      관심사 일치 ({currentInterest})
                    </span>
                    <span className="value">{interestMatchRate}%</span>
                  </div>
                  <Track>
                    <Fill
                      value={interestMatchRate}
                      color="#10b981"
                    />
                  </Track>
                </ProgressItem>

                <ProgressItem>
                  <div className="bar-info">
                    <span className="label">이벤트 적합도</span>
                    <span className="value">{eventFitScore}%</span>
                  </div>
                  <Track>
                    <Fill
                      value={eventFitScore}
                      color="#6366f1"
                    />
                  </Track>
                </ProgressItem>

                <ProgressItem>
                  <div className="bar-info">
                    <span className="label">단골 고객 비중</span>
                    <span className="value">{regularCustomerRatio}%</span>
                  </div>
                  <Track>
                    <Fill
                      value={regularCustomerRatio}
                      color="#6366f1"
                    />
                  </Track>
                </ProgressItem>
              </ProgressBarList>
            </ScoreDetailsGrid>
          </Card>

          <Card>
            <CardHeader>
              <div className="card-title-row">
                <h3>노출 대상 고객</h3>
                <TotalBadge>
                  총 {estimatedTargetCount.toLocaleString()}명
                </TotalBadge>
              </div>
              <p>지금 노출하면 닿을 수 있는 생활권 세그먼트</p>
            </CardHeader>

            <SegmentList>
              <SegmentItem>
                <div className="left">
                  <Users
                    size={18}
                    color="#6366f1"
                  />
                  <span>마포구 한식 관심 고객</span>
                </div>
                <div className="right">
                  <TagTypeBadge type="핵심">핵심</TagTypeBadge>
                  <span className="count">1,820명</span>
                </div>
              </SegmentItem>

              <SegmentItem>
                <div className="left">
                  <Users
                    size={18}
                    color="#6366f1"
                  />
                  <span>점심 이벤트 반응 고객</span>
                </div>
                <div className="right">
                  <TagTypeBadge type="추천">추천</TagTypeBadge>
                  <span className="count">940명</span>
                </div>
              </SegmentItem>

              <SegmentItem>
                <div className="left">
                  <Users
                    size={18}
                    color="#6366f1"
                  />
                  <span>우리 가게 단골</span>
                </div>
                <div className="right">
                  <TagTypeBadge type="유지">유지</TagTypeBadge>
                  <span className="count">89명</span>
                </div>
              </SegmentItem>

              <SegmentItem>
                <div className="left">
                  <Users
                    size={18}
                    color="#6366f1"
                  />
                  <span>반경 {radiusKm}km 신규 유입</span>
                </div>
                <div className="right">
                  <TagTypeBadge type="확장">확장</TagTypeBadge>
                  <span className="count">610명</span>
                </div>
              </SegmentItem>
            </SegmentList>
          </Card>

          <Card>
            <CardHeader>
              <h3>매칭 이유</h3>
            </CardHeader>

            <TagGroup>
              {segments.map((tag, idx) => (
                <KeywordTag key={idx}>#{tag}</KeywordTag>
              ))}
            </TagGroup>

            <ReasonList>
              <ReasonItem>
                <CheckCircle2
                  size={18}
                  color="#10b981"
                  style={{ flexShrink: 0, marginTop: 2 }}
                />
                <span>
                  주변 생활권 고객의{' '}
                  <strong>관심사 1위가 '{currentInterest}'</strong>으로 우리
                  가게와 일치해요.
                </span>
              </ReasonItem>
              <ReasonItem>
                <CheckCircle2
                  size={18}
                  color="#10b981"
                  style={{ flexShrink: 0, marginTop: 2 }}
                />
                <span>
                  진행 중인 <strong>점심 이벤트</strong>가 점심 수요가 높은
                  직장인 생활권과 잘 맞습니다.
                </span>
              </ReasonItem>
            </ReasonList>
          </Card>

          <FooterText>
            출처: 이음 활동 지역·조회·단골 데이터 + 소상공인 상권 보조 데이터
          </FooterText>
        </LeftColumn>

        <RightColumn>
          <RightChartCard>
            <span className="card-label">종합 매칭 점수</span>
            <DonutContainer style={{ width: 140, height: 140 }}>
              <DonutGraphic $score={totalScore} />
              <DonutScoreText style={{ fontSize: 32 }}>
                {totalScore}
                <span>점</span>
              </DonutScoreText>
            </DonutContainer>
            <span className="percentile-text">상위 8% · 매우 높음</span>
          </RightChartCard>

          {isExposing ? (
            <ActiveStatusButton onClick={handleStopExposure}>
              <Radio size={18} /> 노출 진행 중 · 클릭 시 중지
            </ActiveStatusButton>
          ) : (
            <PrimaryButton onClick={handleStartExposure}>
              <Users size={18} /> 노출 시작하기
            </PrimaryButton>
          )}

          <SecondaryButton onClick={() => setIsModalOpen(true)}>
            <Sliders size={18} /> 대상 조건 변경
          </SecondaryButton>
        </RightColumn>
      </ContentGrid>

      {/* 대상 조건 변경 Drawer */}
      <Overlay
        $isVisible={isModalOpen}
        onClick={() => setIsModalOpen(false)}
      />
      <Drawer $isVisible={isModalOpen}>
        <DrawerHeader>
          <h2>대상 조건 변경</h2>
          <CloseButton onClick={() => setIsModalOpen(false)}>
            <X size={20} />
          </CloseButton>
        </DrawerHeader>

        <DrawerContent>
          <FormGroup>
            <label>노출 반경</label>
            <SegmentedControl>
              {['1km', '1.5km', '3km'].map((opt) => (
                <SegmentOption
                  key={opt}
                  $active={radiusKm === parseFloat(opt)}
                  onClick={() => handleRadiusChange(opt)}
                >
                  {opt}
                </SegmentOption>
              ))}
            </SegmentedControl>
            <span className="sub-desc">우리 가게 기준 반경</span>
          </FormGroup>

          <FormGroup>
            <label>관심사</label>
            <CustomSelectWrapper>
              <SelectTrigger onClick={() => setIsSelectOpen((prev) => !prev)}>
                <span>{currentInterestLabel}</span>
                <ChevronDown
                  size={18}
                  color="#94a3b8"
                />
              </SelectTrigger>

              {isSelectOpen && (
                <DropdownMenu>
                  {interestOptions.map((opt) => (
                    <DropdownItem
                      key={opt.value}
                      $selected={interest === opt.value}
                      onClick={() => handleInterestSelect(opt.value)}
                    >
                      <Check className="check-icon" />
                      <span>{opt.label}</span>
                    </DropdownItem>
                  ))}
                </DropdownMenu>
              )}
            </CustomSelectWrapper>
          </FormGroup>

          <FormGroup>
            <label>고객 유형</label>
            <SegmentedControl>
              {['전체', '단골', '신규'].map((label) => (
                <SegmentOption
                  key={label}
                  $active={customerTypeMap[customerType] === label}
                  onClick={() => handleCustomerTypeChange(label)}
                >
                  {label}
                </SegmentOption>
              ))}
            </SegmentedControl>
          </FormGroup>

          <ResultBanner>
            <span className="label">변경 후 추정 노출 대상</span>
            <span className="count">
              약 {estimatedTargetCount.toLocaleString()}명
            </span>
          </ResultBanner>
        </DrawerContent>

        <DrawerFooter>
          <CancelButton onClick={() => setIsModalOpen(false)}>
            취소
          </CancelButton>
          <ApplyButton onClick={handleApplyConditions}>
            <Check size={18} /> 조건 적용
          </ApplyButton>
        </DrawerFooter>
      </Drawer>

      {/* 플랜 업그레이드 모달 */}
      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => setIsUpgradeModalOpen(false)}
        errorMessage={upgradeErrorMessage}
      />
    </PageContainer>
  );
}
