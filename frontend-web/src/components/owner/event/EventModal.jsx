import React, { useState, useEffect, useMemo } from 'react';
import styled from 'styled-components';
import { X } from 'lucide-react';
import { eventApi } from '../../../api/owner/eventApi';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalContainer = styled.div`
  background: #ffffff;
  border-radius: 20px;
  width: 500px;
  max-width: 90%;
  padding: 24px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  position: relative;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    font-size: 18px;
    font-weight: bold;
    color: #1a1f2c;
    margin: 0;
  }

  button.close-btn {
    background: none;
    border: none;
    cursor: pointer;
    color: #8e94a0;
    display: flex;
    align-items: center;
    &:hover {
      color: #1a1f2c;
    }
  }
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;

  label {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }

  select,
  input[type='text'],
  input[type='number'] {
    width: 100%;
    padding: 10px 14px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;
    &:focus {
      border-color: #00a651;
    }
  }

  .base-price {
    font-size: 12px;
    color: #64748b;
    margin-top: 2px;
  }
`;

const FormGroupRow = styled.div`
  display: flex;
  gap: 16px;
  width: 100%;
  margin-bottom: 20px;
  box-sizing: border-box;

  .form-group-half {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  label {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }

  input[type='datetime-local'] {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    font-size: 14px;
    outline: none;
    box-sizing: border-box;
    &:focus {
      border-color: #00a651;
    }
  }
`;

const PriceSectionBox = styled.div`
  background: #f4fbf7;
  border: 1px solid #e2f5ec;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .section-title {
    font-size: 13px;
    font-weight: 600;
    color: #2e7d32;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .hint-text {
    font-size: 11px;
    color: #8e94a0;
    margin-top: -6px;
  }
`;

const PriceInputContainer = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 12px;
    color: #475569;
    font-weight: 500;
  }

  .arrow-text {
    font-size: 12px;
    color: #00a651;
    font-weight: 500;
    margin-top: 2px;
  }
`;

const InputWrapper = styled.div`
  display: flex;
  align-items: center;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0 12px;
  height: 40px;
  box-sizing: border-box;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: #00a651;
  }

  input {
    flex: 1;
    border: none !important;
    outline: none !important;
    padding: 0 !important;
    height: 100%;
    font-size: 14px;
    color: #333;

    &::-webkit-outer-spin-button,
    &::-webkit-inner-spin-button {
      -webkit-appearance: none;
      margin: 0;
    }
  }

  .unit {
    font-size: 13px;
    color: #94a3b8;
    font-weight: 500;
    margin-left: 6px;
    white-space: nowrap;
  }
`;

const PriceResultBar = styled.div`
  background: #e6f7ee;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;

  .price-flex {
    display: flex;
    align-items: center;
    gap: 8px;

    .original {
      color: #94a3b8;
      text-decoration: line-through;
      font-size: 13px;
    }
    .discounted {
      color: #e52e59;
      font-weight: bold;
      font-size: 16px;
    }
    .badge {
      background: #e52e59;
      color: #fff;
      font-size: 11px;
      font-weight: bold;
      padding: 2px 6px;
      border-radius: 4px;
    }
  }

  .calc-gap {
    font-size: 12px;
    color: #64748b;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;

  button {
    padding: 10px 20px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: bold;
    cursor: pointer;
    border: none;
  }

  button.cancel-btn {
    background: #f1f5f9;
    color: #475569;
    &:hover {
      background: #e2e8f0;
    }
  }
`;

const SubmitButton = styled.button`
  background: ${(props) => (props.$isReady ? '#bcf0da' : '#cbd5e1')};
  color: ${(props) => (props.$isReady ? '#00a651' : '#94a3b8')};
  cursor: ${(props) => (props.$isReady ? 'pointer' : 'not-allowed')} !important;
  transition: background 0.2s;
`;

function EventModal({ isOpen, onClose, onSave, editingEvent }) {
  const [productList, setProductList] = useState([]);

  // 상태값 설정 (초기 원가는 0으로 세팅)
  const [productId, setProductId] = useState('');
  const [baseOriginalPrice, setBaseOriginalPrice] = useState(0);
  const [discountRate, setDiscountRate] = useState(0);
  const [discountedPrice, setDiscountedPrice] = useState(0);
  const [quantity, setQuantity] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');

  useEffect(() => {
    if (!isOpen) return;

    const fetchProducts = async () => {
      try {
        const response = await eventApi.getOwnerProducts();
        if (response.data && response.data.success) {
          // status가 INACTIVE가 아닌 상품 필터링
          const activeProducts = response.data.data.filter(
            (product) => product.status !== 'INACTIVE',
          );
          setProductList(activeProducts);
        }
      } catch (error) {
        console.error('모달 내 상품 목록 가져오기 실패:', error);
      }
    };

    fetchProducts();
  }, [isOpen]);

  // 수정 모드일 때 기존 데이터 불러오기 바인딩
  useEffect(() => {
    if (editingEvent) {
      setProductId(editingEvent.productId || '');
      setBaseOriginalPrice(editingEvent.originalPrice || 0);
      setDiscountedPrice(editingEvent.eventPrice || 0);
      setQuantity(editingEvent.eventStock || '');

      setStartTime(
        editingEvent.startAt ? editingEvent.startAt.substring(0, 16) : '',
      );
      setEndTime(editingEvent.endAt ? editingEvent.endAt.substring(0, 16) : '');

      if (editingEvent.discountRate !== undefined) {
        setDiscountRate(editingEvent.discountRate);
      } else if (editingEvent.originalPrice && editingEvent.eventPrice) {
        const calculatedRate = Math.round(
          ((editingEvent.originalPrice - editingEvent.eventPrice) /
            editingEvent.originalPrice) *
            100,
        );
        setDiscountRate(calculatedRate);
      }
    } else {
      // 등록 모드일 때 전체 초기화
      setProductId('');
      setBaseOriginalPrice(0);
      setDiscountRate(0);
      setDiscountedPrice(0);
      setQuantity('');
      setStartTime('');
      setEndTime('');
    }
  }, [editingEvent, isOpen]);

  // 드롭다운 선택 시 해당 상품의 원가를 찾아 자동으로 상태 주입하는 핸들러
  const handleProductChange = (e) => {
    const selectedId = Number(e.target.value);
    setProductId(selectedId);

    const matchProduct = productList.find((p) => p.productId === selectedId);
    if (matchProduct) {
      setBaseOriginalPrice(matchProduct.price);
      setDiscountedPrice(matchProduct.price); // 초기 이벤트가는 원가와 동일하게 설정
      setDiscountRate(0);
    } else {
      setBaseOriginalPrice(0);
      setDiscountedPrice(0);
      setDiscountRate(0);
    }
  };

  const handleDiscountRateChange = (e) => {
    let value = Number(e.target.value);

    if (value > 100) {
      alert('할인율은 100%를 초과할 수 없습니다.');
      value = 100;
    }
    if (value < 0) value = 0;

    setDiscountRate(value);
    const calculatedPrice = Math.round(baseOriginalPrice * (1 - value / 100));
    setDiscountedPrice(calculatedPrice);
  };

  const handleEventPriceChange = (e) => {
    let value = Number(e.target.value);

    if (value < 0) value = 0;

    setDiscountedPrice(value);
    if (baseOriginalPrice > 0) {
      const calculatedRate = Math.round(
        ((baseOriginalPrice - value) / baseOriginalPrice) * 100,
      );
      setDiscountRate(
        calculatedRate < 0 ? 0 : calculatedRate > 100 ? 100 : calculatedRate,
      );
    }
  };

  // 모든 필수 폼 작성 확인 검사 (동일 유지)
  const isFormValid = useMemo(() => {
    return (
      productId !== '' &&
      discountedPrice > 0 &&
      discountRate <= 100 &&
      quantity > 0 &&
      startTime !== '' &&
      endTime !== ''
    );
  }, [productId, discountedPrice, discountRate, quantity, startTime, endTime]);

  const handleSubmit = (e) => {
    e.preventDefault();

    if (discountedPrice <= 0) {
      alert('이벤트 가격은 0원 이하가 될 수 없습니다.');
      return;
    }
    if (discountRate > 100) {
      alert('할인율은 100%를 넘을 수 없습니다.');
      return;
    }

    if (startTime && endTime) {
      const startObj = new Date(startTime);
      const endObj = new Date(endTime);

      // 새 등록 모드일 때만 시작 시간이 현재 시간보다 과거인지 검사
      if (!editingEvent) {
        const now = new Date();
        if (startObj < now) {
          alert('시작 시간은 현재 시간보다 이후여야 합니다.');
          return;
        }
      }

      // 종료 시간이 시작 시간보다 빠른지 검사
      if (endObj <= startObj) {
        alert('종료 시간은 시작 시간보다 이후로 설정해야 합니다.');
        return;
      }
    }

    const formattedStartAt = startTime ? `${startTime}` : '';
    const formattedEndAt = endTime ? `${endTime}` : '';

    onSave({
      productId: Number(productId),
      eventPrice: Number(discountedPrice),
      eventStock: Number(quantity),
      startAt: formattedStartAt,
      endAt: formattedEndAt,
    });

    onClose();
  };

  if (!isOpen) return null;

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <h2>{editingEvent ? '이벤트 정보 수정' : '새 이벤트 등록'}</h2>
          <button className="close-btn" onClick={onClose}>
            <X size={20} />
          </button>
        </ModalHeader>

        <form onSubmit={handleSubmit}>
          <FormGroup>
            <label>상품 선택 *</label>
            <select
              value={productId}
              onChange={handleProductChange}
              disabled={!!editingEvent}
            >
              <option value="">상품을 선택하세요</option>
              {productList.map((product) => (
                <option key={product.productId} value={product.productId}>
                  {product.name} — {product.price.toLocaleString()}원
                </option>
              ))}
            </select>
            <span className="base-price">
              기본 가격: {baseOriginalPrice.toLocaleString()}원
            </span>
          </FormGroup>

          <PriceSectionBox>
            <div className="section-title">🍱 이벤트 가격 설정</div>
            <div className="hint-text">
              할인율 또는 이벤트 가격 중 하나만 입력하시면 나머지가 자동
              계산됩니다.
            </div>

            <div style={{ display: 'flex', gap: '16px', width: '100%' }}>
              <PriceInputContainer>
                <label>할인율 (%)</label>
                <InputWrapper>
                  <input
                    type="number"
                    placeholder="예: 25"
                    min="0"
                    max="100"
                    value={discountRate === 0 ? '' : discountRate}
                    onChange={handleDiscountRateChange}
                  />
                  <span className="unit">%</span>
                </InputWrapper>
                <span className="arrow-text">
                  ➔ 이벤트가: {discountedPrice.toLocaleString()}원
                </span>
              </PriceInputContainer>

              <PriceInputContainer>
                <label>이벤트 가격 (원)</label>
                <InputWrapper>
                  <input
                    type="number"
                    placeholder="예: 7500"
                    min="1"
                    value={discountedPrice === 0 ? '' : discountedPrice}
                    onChange={handleEventPriceChange}
                  />
                  <span className="unit">원</span>
                </InputWrapper>
                <span className="arrow-text">➔ 할인율: {discountRate}%</span>
              </PriceInputContainer>
            </div>

            <PriceResultBar>
              <div className="price-flex">
                <span className="original">
                  {baseOriginalPrice.toLocaleString()}원
                </span>
                <span className="discounted">
                  {discountedPrice.toLocaleString()}원
                </span>
                <span className="badge">-{discountRate}%</span>
              </div>
              <div className="calc-gap">
                할인금액:{' '}
                {(baseOriginalPrice - discountedPrice).toLocaleString()}원
              </div>
            </PriceResultBar>
          </PriceSectionBox>

          <FormGroup>
            <label>이벤트 수량 *</label>
            <input
              type="number"
              placeholder="한정 수량을 입력하세요"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </FormGroup>

          <FormGroupRow>
            <div className="form-group-half">
              <label>시작 시간 *</label>
              <input
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </div>
            <div className="form-group-half">
              <label>종료 시간 *</label>
              <input
                type="datetime-local"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>
          </FormGroupRow>

          <ButtonGroup>
            <button type="button" className="cancel-btn" onClick={onClose}>
              취소
            </button>
            <SubmitButton
              type="submit"
              $isReady={isFormValid}
              disabled={!isFormValid}
            >
              {editingEvent ? '수정완료' : '등록하기'}
            </SubmitButton>
          </ButtonGroup>
        </form>
      </ModalContainer>
    </ModalOverlay>
  );
}

export default EventModal;
