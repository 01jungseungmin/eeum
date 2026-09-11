import React, { useState } from 'react';
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

export default function AiCreateEventPage() {
  const navigate = useNavigate();

  // 통합 폼 상태 관리
  const [formData, setFormData] = useState({
    selectedProduct: '1', // '1': 김치찌개, '2': 된장찌개, '3': 불고기
    discountType: 'percent', // 'percent' | 'amount' | 'service'
    discountValue: '10',
    startDate: '2026. 05. 31.',
    endDate: '2026. 06. 30.',
    timeType: 'custom', // 'all' | 'custom'
    startTime: '오전 11:00',
    endTime: '오후 02:00',
    quantityType: 'none', // 'none' | 'limit'
    isMatchingScore: true,
  });

  // 상태 변경 공통 핸들러
  const handleChange = (key, value) => {
    setFormData((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  // 상품 정보 리스트
  const products = [
    {
      id: '1',
      name: '김치찌개 반찬 세트',
      category: '세트메뉴',
      price: '12,000원',
    },
    { id: '2', name: '된장찌개 반찬', category: '국·찌개', price: '8,000원' },
    {
      id: '3',
      name: '불고기 반찬 (300g)',
      category: '반찬류',
      price: '12,000원',
    },
  ];

  // 현재 선택된 상품 정보
  const currentProduct = products.find(
    (p) => p.id === formData.selectedProduct,
  );

  const handleSubmit = () => {
    console.log('제출 데이터:', formData);
    alert('이벤트가 등록되었습니다.');
    navigate(-1);
  };

  return (
    <PageWrapper>
      {/* 뒤로가기 버튼 */}
      <BackButton onClick={() => navigate(-1)}>
        <ArrowLeft size={18} />
        <span>AI 매니저로 돌아가기</span>
      </BackButton>

      {/* AI 추천 요약 배너 */}
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
            AI가 추천한 이벤트 ·{' '}
            <strong>김치찌개 반찬 세트 · 평일 점심 10% 할인</strong>
          </p>
        </AiBannerText>
      </AiBanner>

      <ContentGrid>
        {/* 좌측 입력 폼 영역 */}
        <LeftColumn>
          {/* 1. 이벤트 대상 상품 */}
          <Card>
            <CardHeader>
              <h2>이벤트 대상 상품</h2>
              <p>이벤트를 적용할 상품을 선택하세요</p>
            </CardHeader>
            <ProductList>
              {products.map((item) => {
                const isSelected = formData.selectedProduct === item.id;
                return (
                  <ProductCard
                    key={item.id}
                    $isSelected={isSelected}
                    onClick={() => handleChange('selectedProduct', item.id)}
                  >
                    <ProductLeft>
                      <ProductIconBox $isSelected={isSelected}>
                        <UtensilsCrossed size={18} />
                      </ProductIconBox>
                      <ProductInfo>
                        <h4>{item.name}</h4>
                        <p>
                          {item.category} · {item.price}
                        </p>
                      </ProductInfo>
                    </ProductLeft>
                    <RadioCircle $isSelected={isSelected}>
                      {isSelected && <RadioInner />}
                    </RadioCircle>
                  </ProductCard>
                );
              })}
            </ProductList>
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

            <FormGroup style={{ marginTop: 16 }}>
              <Label>할인율</Label>
              <InputWithUnit>
                <input
                  type="text"
                  value={formData.discountValue}
                  onChange={(e) =>
                    handleChange('discountValue', e.target.value)
                  }
                />
                <span>{formData.discountType === 'percent' ? '%' : '원'}</span>
              </InputWithUnit>
              <HelperText>1~100% 사이로 입력하세요</HelperText>
            </FormGroup>
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
                    type="text"
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
                    type="text"
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
                      type="text"
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
                      type="text"
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
                  {formData.discountValue}
                  {formData.discountType === 'percent' ? '% 할인' : '원 할인'}
                </PreviewHighlight>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>기간</PreviewLabel>
                <PreviewValue>
                  {formData.startDate.slice(6, 11)} ~{' '}
                  {formData.endDate.slice(6, 11)}
                </PreviewValue>
              </PreviewRow>
              <PreviewRow>
                <PreviewLabel>시간대</PreviewLabel>
                <PreviewValue>
                  {formData.timeType === 'custom'
                    ? `${formData.startTime.replace('오전 ', '').replace('오후 ', '')}~${formData.endTime.replace('오전 ', '').replace('오후 ', '')}`
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
            <SubmitButton onClick={handleSubmit}>
              <Check size={18} /> 등록하기
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
