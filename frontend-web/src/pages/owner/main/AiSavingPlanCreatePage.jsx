import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { ArrowLeft, Sparkles, Check, Leaf, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';
import PlanUpgradeModal from '../../../components/owner/ai/modal/PlanUpgradeModal';

/* 기존 Styled Components 유지 */
const Container = styled.div`
  max-width: 1080px;
  margin: 0 auto;
  padding: 32px 24px;
  background-color: #f9fafb;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  color: #6b7280;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 0;
  width: fit-content;
  &:hover {
    color: #111827;
  }
`;

const HeaderSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    h2 {
      font-size: 22px;
      font-weight: 700;
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

const AiBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background-color: #f0fdf4;
  color: #16a34a;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 12px;
  border: 1px solid #bbf7d0;
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 24px;
  align-items: start;
  @media (max-width: 868px) {
    grid-template-columns: 1fr;
  }
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const Card = styled.div`
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CardHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  h3 {
    font-size: 16px;
    font-weight: 700;
    color: #111827;
    margin: 0;
  }
  span {
    font-size: 12px;
    color: #9ca3af;
  }
`;

const ItemList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ItemRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 10px;
  border: 1px solid ${(props) => (props.$selected ? '#a7f3d0' : '#e5e7eb')};
  background-color: ${(props) => (props.$selected ? '#fafdfb' : '#ffffff')};
  transition: all 0.2s ease;
`;

const ItemLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const IconCircle = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #f0fdf4;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #16a34a;
`;

const ItemInfo = styled.div`
  display: flex;
  flex-direction: column;
  .title {
    font-size: 14px;
    font-weight: 700;
    color: #1f2937;
  }
  .sub {
    font-size: 11px;
    color: #9ca3af;
    margin-top: 2px;
  }
`;

const ItemRight = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  .amount {
    font-size: 14px;
    font-weight: 700;
    color: #f97316;
  }
`;

const ToggleSwitch = styled.label`
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  input {
    opacity: 0;
    width: 0;
    height: 0;
  }
  .slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #e5e7eb;
    transition: 0.3s;
    border-radius: 24px;
    &::before {
      position: absolute;
      content: '';
      height: 18px;
      width: 18px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: 0.3s;
      border-radius: 50%;
    }
  }
  input:checked + .slider {
    background-color: #10b981;
  }
  input:checked + .slider::before {
    transform: translateX(20px);
  }
`;

const ScheduleList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ScheduleItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  .num {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background-color: #f3f4f6;
    color: #6b7280;
    font-size: 11px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    margin-top: 2px;
  }
  .content {
    display: flex;
    flex-direction: column;
    .title {
      font-size: 13px;
      font-weight: 700;
      color: #374151;
    }
    .sub {
      font-size: 11px;
      color: #9ca3af;
    }
  }
`;

const RightSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const SavingBox = styled.div`
  background-color: #f59e0b;
  border-radius: 12px;
  padding: 20px;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 4px;
  .label {
    font-size: 11px;
    opacity: 0.9;
  }
  .amount {
    font-size: 26px;
    font-weight: 800;
    span {
      font-size: 18px;
      font-weight: 600;
      margin-left: 2px;
    }
  }
  .sub {
    font-size: 11px;
    opacity: 0.8;
    margin-top: 4px;
  }
`;

const SaveButton = styled.button`
  width: 100%;
  padding: 12px;
  background-color: #10b981;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  &:hover {
    background-color: #059669;
  }
  &:disabled {
    background-color: #d1d5db;
    cursor: not-allowed;
  }
`;

const CancelButton = styled.button`
  width: 100%;
  background: none;
  border: none;
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  padding: 8px;
  &:hover {
    color: #111827;
  }
`;

const SourceFooter = styled.div`
  font-size: 11px;
  color: #9ca3af;
  margin-top: 12px;
`;

export default function AiSavingPlanCreatePage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [planId, setPlanId] = useState(null);
  const [items, setItems] = useState([]);

  // 모달 상태 제어
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState(false);
  const [modalErrorMessage, setModalErrorMessage] = useState('');

  const [isInitError, setIsInitError] = useState(false);

  // 403 권한 에러 공통 처리 함수
  const handleApiError = (err) => {
    const status = err.response?.status;
    const errorData = err.response?.data?.error;

    if (status === 403 || errorData?.code === 'AI_001') {
      setModalErrorMessage(
        errorData?.message ||
          '현재 플랜에서 사용할 수 없는 기능입니다. 플랜 업그레이드가 필요합니다.',
      );
      setIsUpgradeModalOpen(true);
      return true;
    }
    return false;
  };

  useEffect(() => {
    const initPlan = async () => {
      try {
        setLoading(true);
        const res = await aiManagerApi.createReductionPlan();
        if (res.data?.success) {
          setPlanId(res.data.data.savingPlanId);
          setItems(res.data.data.items || []);
        }
      } catch (err) {
        if (!handleApiError(err)) {
          console.error('절감 계획 생성 실패:', err);
        } else {
          setIsInitError(true);
        }
      } finally {
        setLoading(false);
      }
    };

    initPlan();
  }, []);

  const handleToggle = (index) => {
    setItems((prev) =>
      prev.map((item, idx) =>
        idx === index ? { ...item, selected: !item.selected } : item,
      ),
    );
  };

  const selectedItems = items.filter((item) => item.selected);
  const totalSavingAmount = selectedItems.reduce(
    (sum, item) => sum + (item.expectedMonthlySavingAmount || 0),
    0,
  );

  const formatAmountToTenThousand = (amount) => {
    return (amount / 10000).toFixed(1).replace(/\.0$/, '');
  };

  const handleSavePlan = async () => {
    try {
      setSubmitting(true);
      const payload = {
        savingPlanId: planId,
        items: items.map((item) => ({
          title: item.title,
          selected: item.selected,
        })),
      };

      const res = await aiManagerApi.saveReductionPlan(payload);
      if (res.data?.success) {
        alert('절감 계획이 저장되었습니다.');
        navigate(-1);
      }
    } catch (err) {
      if (!handleApiError(err)) {
        alert('저장 중 오류가 발생했습니다.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container>
      <BackButton onClick={() => navigate(-1)}>
        <ArrowLeft size={16} /> 운영 위험 조기경보로 돌아가기
      </BackButton>

      <HeaderSection>
        <div className="title-row">
          <h2>절감 계획 만들기</h2>
          <AiBadge>
            <Sparkles size={12} /> AI 분석 완료
          </AiBadge>
        </div>
        <p>
          실행할 절감 항목을 선택하시면 예상 절감액과 실행 일정을 정리해 드려요.
        </p>
      </HeaderSection>

      {loading ? (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            padding: '100px',
          }}
        >
          <Loader2
            size={32}
            className="animate-spin"
            color="#10b981"
          />
        </div>
      ) : (
        <MainGrid>
          <LeftSection>
            <Card>
              <CardHeader>
                <h3>절감 항목 선택</h3>
                <span>포함할 항목을 켜고 끌 수 있어요</span>
              </CardHeader>

              <ItemList>
                {items.map((item, idx) => (
                  <ItemRow
                    key={idx}
                    $selected={item.selected}
                  >
                    <ItemLeft>
                      <IconCircle>
                        <Leaf size={18} />
                      </IconCircle>
                      <ItemInfo>
                        <span className="title">{item.title}</span>
                        <span className="sub">
                          {item.difficulty} · {item.startTiming}
                        </span>
                      </ItemInfo>
                    </ItemLeft>

                    <ItemRight>
                      <span className="amount">
                        월{' '}
                        {formatAmountToTenThousand(
                          item.expectedMonthlySavingAmount,
                        )}
                        만원
                      </span>
                      <ToggleSwitch>
                        <input
                          type="checkbox"
                          checked={item.selected}
                          onChange={() => handleToggle(idx)}
                        />
                        <span className="slider" />
                      </ToggleSwitch>
                    </ItemRight>
                  </ItemRow>
                ))}
              </ItemList>
            </Card>

            <Card>
              <CardHeader>
                <h3>실행 일정</h3>
                <span>선택된 항목의 권장 진행 순서</span>
              </CardHeader>

              <ScheduleList>
                {selectedItems.map((item, idx) => (
                  <ScheduleItem key={idx}>
                    <div className="num">{idx + 1}</div>
                    <div className="content">
                      <span className="title">{item.title}</span>
                      <span className="sub">
                        {item.startTiming} · {item.difficulty}
                      </span>
                    </div>
                  </ScheduleItem>
                ))}
              </ScheduleList>
            </Card>
          </LeftSection>

          <RightSection>
            <SavingBox>
              <div className="label">추정 월 절감액</div>
              <div className="amount">
                {formatAmountToTenThousand(totalSavingAmount)}
                <span>만원</span>
              </div>
              <div className="sub">
                선택한 {selectedItems.length}개 항목 기준
              </div>
            </SavingBox>

            <SaveButton
              onClick={handleSavePlan}
              disabled={submitting}
            >
              {submitting ? (
                <Loader2
                  size={16}
                  className="animate-spin"
                />
              ) : (
                <Check size={16} />
              )}
              계획 저장하기
            </SaveButton>

            <CancelButton onClick={() => navigate(-1)}>취소</CancelButton>
          </RightSection>
        </MainGrid>
      )}

      <SourceFooter>
        출처: 한국전력공사 전력사용량 데이터 + 업종 운영 데이터
      </SourceFooter>

      {/* 구독 업그레이드 안내 모달 */}
      <PlanUpgradeModal
        isOpen={isUpgradeModalOpen}
        onClose={() => {
          setIsUpgradeModalOpen(false);
          if (isInitError) {
            navigate(-1);
          }
        }}
        errorMessage={modalErrorMessage}
      />
    </Container>
  );
}
