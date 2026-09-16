import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import {
  ArrowLeft,
  Sparkles,
  UtensilsCrossed,
  Check,
  Calendar,
  Clock,
} from 'lucide-react';
import { eventApi } from '../../../api/owner/eventApi';
import { aiManagerApi } from '../../../api/owner/aiManagerApi';

const todayStr = () => new Date().toISOString().slice(0, 10);
const weekLaterStr = () => {
  const d = new Date();
  d.setDate(d.getDate() + 7);
  return d.toISOString().slice(0, 10);
};

// 수량 제한을 두지 않을 때 백엔드에 보낼 재고 수 (백엔드는 무제한 재고 개념이 없어 큰 값으로 대체)
const UNLIMITED_STOCK = 999;

export default function AiCreateEventPage() {
  const navigate = useNavigate();

  const [products, setProducts] = useState([]);
  const [recommendation, setRecommendation] = useState(null);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // 통합 폼 상태 관리
  const [formData, setFormData] = useState({
    selectedProduct: '',
    discountType: 'percent', // 'percent' | 'amount' | 'service'
    discountValue: '10',
    startDate: todayStr(),
    endDate: weekLaterStr(),
    timeType: 'custom', // 'all' | 'custom'
    startTime: '11:00',
    endTime: '14:00',
    quantityType: 'none', // 'none' | 'limit'
    quantity: '30',
    isMatchingScore: true,
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoadingProducts(true);
        const [productsRes, perfRes] = await Promise.all([
          eventApi.getOwnerProducts(),
          aiManagerApi.getEventPerformance(),
        ]);

        let activeProducts = [];
        if (productsRes.data?.success) {
          activeProducts = (productsRes.data.data || []).filter(
            (product) => product.status !== 'INACTIVE',
          );
          setProducts(activeProducts);
        }

        const rec = perfRes.data?.success
          ? perfRes.data.data?.nextEventRecommendation
          : null;
        setRecommendation(rec || null);

        // AI 추천값으로 폼 기본값 채우기 (추천이 없으면 첫 상품만 선택)
        setFormData((prev) => {
          const next = { ...prev };
          const recommendedActive =
            rec &&
            activeProducts.some(
              (p) => p.productId === rec.recommendedProductId,
            );

          if (recommendedActive) {
            next.selectedProduct = String(rec.recommendedProductId);
          } else if (activeProducts.length > 0) {
            next.selectedProduct = String(activeProducts[0].productId);
          }

          if (recommendedActive && rec.discountType) {
            next.discountType = rec.discountType.toLowerCase();
            if (rec.discountType === 'PERCENT' && rec.discountRate != null) {
              next.discountValue = String(rec.discountRate);
            } else if (
              rec.discountType === 'AMOUNT' &&
              rec.discountAmount != null
            ) {
              next.discountValue = String(rec.discountAmount);
            }
          }

          if (recommendedActive && rec.recommendedTimeRange?.includes('~')) {
            const [start, end] = rec.recommendedTimeRange
              .split('~')
              .map((s) => s.trim());
            next.timeType = 'custom';
            next.startTime = start;
            next.endTime = end;
          }

          if (recommendedActive && rec.matchBasedExposure !== undefined) {
            next.isMatchingScore = rec.matchBasedExposure;
          }

          return next;
        });
      } catch (error) {
        console.error('데이터 조회 실패:', error);
      } finally {
        setLoadingProducts(false);
      }
    };

    fetchData();
  }, []);

  // 상태 변경 공통 핸들러
  const handleChange = (key, value) => {
    setFormData((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  // 현재 선택된 상품 정보
  const currentProduct = products.find(
    (p) => String(p.productId) === formData.selectedProduct,
  );

  // 할인 설정에 따른 실제 이벤트 가격 계산 (서비스 제공 = 0원)
  const calcEventPrice = () => {
    const originalPrice = currentProduct?.price || 0;
    const value = Number(formData.discountValue) || 0;

    if (formData.discountType === 'percent') {
      return Math.max(0, Math.round(originalPrice * (1 - value / 100)));
    }
    if (formData.discountType === 'amount') {
      return Math.max(0, originalPrice - value);
    }
    return 0; // service
  };

  const handleSubmit = async () => {
    if (!currentProduct) {
      alert('이벤트를 적용할 상품을 선택해주세요.');
      return;
    }

    const eventPrice = calcEventPrice();
    const eventStock =
      formData.quantityType === 'limit'
        ? Number(formData.quantity)
        : UNLIMITED_STOCK;

    if (formData.quantityType === 'limit' && (!eventStock || eventStock <= 0)) {
      alert('이벤트 수량은 1개 이상이어야 합니다.');
      return;
    }

    const startAt = `${formData.startDate}T${
      formData.timeType === 'custom' ? formData.startTime : '00:00'
    }:00`;
    const endAt = `${formData.endDate}T${
      formData.timeType === 'custom' ? formData.endTime : '23:59'
    }:00`;

    if (new Date(startAt) < new Date()) {
      alert('시작 일시는 현재 시간보다 이후여야 합니다.');
      return;
    }
    if (new Date(endAt) <= new Date(startAt)) {
      alert('종료 일시는 시작 일시보다 이후여야 합니다.');
      return;
    }

    setSubmitting(true);
    try {
      const response = await eventApi.createOwnerEventProduct({
        productId: currentProduct.productId,
        eventPrice,
        eventStock,
        startAt,
        endAt,
      });

      if (response.data?.success) {
        alert('이벤트가 등록되었습니다.');
        navigate(-1);
      } else {
        alert(response.data?.message || '이벤트 등록에 실패했습니다.');
      }
    } catch (error) {
      console.error('이벤트 등록 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '이벤트 등록 중 오류가 발생했습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <PageWrapper>
      {/* 뒤로가기 버튼 */}
      <BackButton onClick={() => navigate(-1)}>
        <ArrowLeft size={18} />
        <span>AI 매니저로 돌아가기</span>
      </BackButton>

      {/* AI 추천 요약 배너 (추천 데이터가 있을 때만) */}
      {recommendation && (
        <AiBanner>
          <AiIconBox>
            <Sparkles
              size={18}
              color="#16a34a"
            />
          </AiIconBox>
          <AiBannerText>
            <strong>AI 추천 요약</strong>
            <p>
              {recommendation.reason ||
                `AI가 추천한 이벤트 · ${recommendation.recommendedProductName || ''}`}
            </p>
          </AiBannerText>
        </AiBanner>
      )}

      <ContentGrid>
        {/* 좌측 입력 폼 영역 */}
        <LeftColumn>
          {/* 1. 이벤트 대상 상품 */}
          <Card>
            <CardHeader>
              <h2>이벤트 대상 상품</h2>
              <p>이벤트를 적용할 상품을 선택하세요</p>
            </CardHeader>
            {loadingProducts ? (
              <EmptyText>상품 목록을 불러오는 중...</EmptyText>
            ) : products.length === 0 ? (
              <EmptyText>등록된 판매 상품이 없습니다.</EmptyText>
            ) : (
              <ProductList>
                {products.map((item) => {
                  const isSelected =
                    formData.selectedProduct === String(item.productId);
                  return (
                    <ProductCard
                      key={item.productId}
                      $isSelected={isSelected}
                      onClick={() =>
                        handleChange('selectedProduct', String(item.productId))
                      }
                    >
                      <ProductLeft>
                        <ProductIconBox $isSelected={isSelected}>
                          <UtensilsCrossed size={18} />
                        </ProductIconBox>
                        <ProductInfo>
                          <h4>{item.name}</h4>
                          <p>{item.price.toLocaleString()}원</p>
                        </ProductInfo>
                      </ProductLeft>
                      <RadioCircle $isSelected={isSelected}>
                        {isSelected && <RadioInner />}
                      </RadioCircle>
                    </ProductCard>
                  );
                })}
              </ProductList>
            )}
          </Card>

          {/* 2. 할인 설정 */}
          <Card>
            <CardHeader>
              <h2>할인 설정</h2>
            </CardHeader>

            <FormGroup>
              <Label>할인 유형</Label>
              <TabContainer>
                <TabButton
                  $active={formData.discountType === 'percent'}
                  onClick={() => handleChange('discountType', 'percent')}
                >
                  퍼센트 할인
                </TabButton>
                <TabButton
                  $active={formData.discountType === 'amount'}
                  onClick={() => handleChange('discountType', 'amount')}
                >
                  금액 할인
                </TabButton>
                <TabButton
                  $active={formData.discountType === 'service'}
                  onClick={() => handleChange('discountType', 'service')}
                >
                  서비스 제공
                </TabButton>
              </TabContainer>
            </FormGroup>

            {formData.discountType !== 'service' && (
              <FormGroup style={{ marginTop: 16 }}>
                <Label>
                  {formData.discountType === 'percent' ? '할인율' : '할인 금액'}
                </Label>
                <InputWithUnit>
                  <input
                    type="number"
                    min="0"
                    max={formData.discountType === 'percent' ? 100 : undefined}
                    value={formData.discountValue}
                    onChange={(e) =>
                      handleChange('discountValue', e.target.value)
                    }
                  />
                  <span>
                    {formData.discountType === 'percent' ? '%' : '원'}
                  </span>
                </InputWithUnit>
                <HelperText>
                  {formData.discountType === 'percent'
                    ? '1~100% 사이로 입력하세요'
                    : '원 단위로 입력하세요'}
                </HelperText>
              </FormGroup>
            )}
            {formData.discountType === 'service' && (
              <HelperText>선택한 상품을 무료로 제공합니다 (0원)</HelperText>
            )}
          </Card>

          {/* 3. 이벤트 기간 */}
          <Card>
            <CardHeader>
              <h2>이벤트 기간</h2>
            </CardHeader>

            <RowGrid>
              <FormGroup>
                <Label>시작일</Label>
                <InputWithIcon>
                  <input
                    type="date"
                    value={formData.startDate}
                    onChange={(e) => handleChange('startDate', e.target.value)}
                  />
                  <Calendar
                    size={16}
                    color="#9ca3af"
                  />
                </InputWithIcon>
              </FormGroup>

              <FormGroup>
                <Label>종료일</Label>
                <InputWithIcon>
                  <input
                    type="date"
                    value={formData.endDate}
                    onChange={(e) => handleChange('endDate', e.target.value)}
                  />
                  <Calendar
                    size={16}
                    color="#9ca3af"
                  />
                </InputWithIcon>
              </FormGroup>
            </RowGrid>

            <FormGroup style={{ marginTop: 20 }}>
              <Label>이벤트 시간대</Label>
              <TabContainer>
                <TabButton
                  $active={formData.timeType === 'all'}
                  onClick={() => handleChange('timeType', 'all')}
                >
                  전체 시간
                </TabButton>
                <TabButton
                  $active={formData.timeType === 'custom'}
                  onClick={() => handleChange('timeType', 'custom')}
                >
                  시간 지정
                </TabButton>
              </TabContainer>
            </FormGroup>

            {formData.timeType === 'custom' && (
              <RowGrid style={{ marginTop: 16 }}>
                <FormGroup>
                  <Label>시작 시간</Label>
                  <InputWithIcon>
                    <input
                      type="time"
                      value={formData.startTime}
                      onChange={(e) =>
                        handleChange('startTime', e.target.value)
                      }
                    />
                    <Clock
                      size={16}
                      color="#9ca3af"
                    />
                  </InputWithIcon>
                </FormGroup>

                <FormGroup>
                  <Label>종료 시간</Label>
                  <InputWithIcon>
                    <input
                      type="time"
                      value={formData.endTime}
                      onChange={(e) => handleChange('endTime', e.target.value)}
                    />
                    <Clock
                      size={16}
                      color="#9ca3af"
                    />
                  </InputWithIcon>
                </FormGroup>
              </RowGrid>
            )}
          </Card>

          {/* 4. 추가 옵션 */}
          <Card>
            <CardHeader>
              <h2>추가 옵션</h2>
            </CardHeader>

            <FormGroup>
              <Label>수량 제한</Label>
              <TabContainer>
                <TabButton
                  $active={formData.quantityType === 'none'}
                  onClick={() => handleChange('quantityType', 'none')}
                >
                  제한 없음
                </TabButton>
                <TabButton
                  $active={formData.quantityType === 'limit'}
                  onClick={() => handleChange('quantityType', 'limit')}
                >
                  수량 지정
                </TabButton>
              </TabContainer>
              {formData.quantityType === 'limit' ? (
                <InputWithUnit style={{ marginTop: 12 }}>
                  <input
                    type="number"
                    min="1"
                    value={formData.quantity}
                    onChange={(e) => handleChange('quantity', e.target.value)}
                  />
                  <span>개</span>
                </InputWithUnit>
              ) : (
                <HelperText>
                  재고를 사실상 무제한({UNLIMITED_STOCK}개)으로 설정합니다.
                </HelperText>
              )}
            </FormGroup>

            <ToggleBox>
              <ToggleText>
                <h4>매칭 점수 기준 노출</h4>
                <p>생활권 매칭 점수가 높은 고객에게 우선 노출해요</p>
              </ToggleText>
              <Switch
                $active={formData.isMatchingScore}
                onClick={() =>
                  handleChange('isMatchingScore', !formData.isMatchingScore)
                }
              >
                <SwitchHandle $active={formData.isMatchingScore} />
              </Switch>
            </ToggleBox>
          </Card>
        </LeftColumn>

        {/* 우측 이벤트 미리보기 영역 */}
        <RightColumn>
          <PreviewCard>
            <PreviewTitle>이벤트 미리보기</PreviewTitle>
            <PreviewList>
              <PreviewRow>
                <PreviewLabel>대상 상품</PreviewLabel>
                <PreviewValue>{currentProduct?.name}</PreviewValue>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>할인</PreviewLabel>
                <PreviewHighlight>
                  {formData.discountType === 'service'
                    ? '무료 제공'
                    : `${formData.discountValue}${
                        formData.discountType === 'percent' ? '% 할인' : '원 할인'
                      }`}
                </PreviewHighlight>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>가격</PreviewLabel>
                <PreviewValue>{calcEventPrice().toLocaleString()}원</PreviewValue>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>기간</PreviewLabel>
                <PreviewValue>
                  {formData.startDate} ~ {formData.endDate}
                </PreviewValue>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>시간대</PreviewLabel>
                <PreviewValue>
                  {formData.timeType === 'custom'
                    ? `${formData.startTime}~${formData.endTime}`
                    : '전체 시간'}
                </PreviewValue>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>노출</PreviewLabel>
                <PreviewValue>
                  {formData.isMatchingScore ? '매칭 점수 기준' : '일반 노출'}
                </PreviewValue>
              </PreviewRow>
            </PreviewList>
          </PreviewCard>

          <SubmitButtonGroup>
            <SubmitButton
              onClick={handleSubmit}
              disabled={submitting || loadingProducts || !currentProduct}
            >
              <Check size={18} /> {submitting ? '등록 중...' : '등록하기'}
            </SubmitButton>
            <CancelButton onClick={() => navigate(-1)}>취소</CancelButton>
          </SubmitButtonGroup>
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
  margin-bottom: 20px;

  &:hover {
    color: #1e293b;
  }
`;

const AiBanner = styled.div`
  background-color: #f0fdf4;
  border: 1px solid #dcfce7;
  border-radius: 16px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
`;

const AiIconBox = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background-color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const AiBannerText = styled.div`
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

    strong {
      color: #0f172a;
    }
  }
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
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
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

const ProductList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const ProductCard = styled.div`
  border: 1px solid ${(props) => (props.$isSelected ? '#41b37d' : '#f1f5f9')};
  background-color: ${(props) => (props.$isSelected ? '#f0fdf4' : '#fafafa')};
  border-radius: 14px;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  transition: all 0.2s;
`;

const ProductLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;
`;

const ProductIconBox = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background-color: #ffffff;
  color: ${(props) => (props.$isSelected ? '#166534' : '#94a3b8')};
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e2e8f0;
`;

const ProductInfo = styled.div`
  h4 {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 2px 0;
  }

  p {
    font-size: 13px;
    color: #64748b;
    margin: 0;
  }
`;

const RadioCircle = styled.div`
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid ${(props) => (props.$isSelected ? '#41b37d' : '#cbd5e1')};
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
`;

const RadioInner = styled.div`
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #41b37d;
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: #334155;
`;

const TabContainer = styled.div`
  background-color: #f1f5f9;
  border-radius: 12px;
  padding: 4px;
  display: flex;
  gap: 4px;
`;

const TabButton = styled.button`
  flex: 1;
  padding: 10px;
  border-radius: 10px;
  border: none;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  background-color: ${(props) => (props.$active ? '#ffffff' : 'transparent')};
  color: ${(props) => (props.$active ? '#0f172a' : '#64748b')};
  box-shadow: ${(props) =>
    props.$active ? '0 1px 3px rgba(0, 0, 0, 0.08)' : 'none'};
  transition: all 0.2s;
`;

const InputWithUnit = styled.div`
  position: relative;
  width: 160px;

  input {
    width: 100%;
    padding: 10px 32px 10px 14px;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;

    &:focus {
      border-color: #41b37d;
    }
  }

  span {
    position: absolute;
    right: 14px;
    top: 50%;
    transform: translateY(-50%);
    font-size: 14px;
    color: #94a3b8;
  }
`;

const HelperText = styled.span`
  font-size: 12px;
  color: #94a3b8;
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 30px 0;
  color: #94a3b8;
  font-size: 13px;
`;

const RowGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
`;

const InputWithIcon = styled.div`
  position: relative;

  input {
    width: 100%;
    padding: 10px 36px 10px 14px;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;

    &:focus {
      border-color: #41b37d;
    }
  }

  svg {
    position: absolute;
    right: 12px;
    top: 50%;
    transform: translateY(-50%);
    pointer-events: none;
  }
`;

const ToggleBox = styled.div`
  background-color: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
`;

const ToggleText = styled.div`
  h4 {
    font-size: 14px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 2px 0;
  }

  p {
    font-size: 12px;
    color: #64748b;
    margin: 0;
  }
`;

const Switch = styled.div`
  width: 44px;
  height: 24px;
  border-radius: 12px;
  background-color: ${(props) => (props.$active ? '#41b37d' : '#cbd5e1')};
  padding: 2px;
  cursor: pointer;
  box-sizing: border-box;
  transition: background-color 0.2s;
`;

const SwitchHandle = styled.div`
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background-color: #ffffff;
  transform: ${(props) =>
    props.$active ? 'translateX(20px)' : 'translateX(0)'};
  transition: transform 0.2s;
`;

const RightColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const PreviewCard = styled.div`
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
`;

const PreviewTitle = styled.h3`
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  margin: 0 0 16px 0;
`;

const PreviewList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const PreviewRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
`;

const PreviewLabel = styled.span`
  color: #64748b;
`;

const PreviewValue = styled.span`
  font-weight: 700;
  color: #0f172a;
`;

const PreviewHighlight = styled.span`
  font-weight: 700;
  color: #f43f5e;
`;

const SubmitButtonGroup = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
`;

const SubmitButton = styled.button`
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
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #369a6a;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const CancelButton = styled.button`
  background: none;
  border: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    color: #0f172a;
  }
`;
