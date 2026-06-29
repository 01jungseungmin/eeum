import React, { useState } from 'react';
import styled from 'styled-components';
import { X, Utensils } from 'lucide-react';
import { approvalApi } from '../../../../api/owner/approvalApi';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease-out;

  @keyframes fadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }
`;

const ModalContent = styled.div`
  background: white;
  padding: 24px;
  border-radius: 16px;
  width: 100%;
  max-width: 480px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  position: relative;
`;

const ModalHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
`;

const HeaderTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  h3 {
    font-size: 18px;
    font-weight: 700;
    color: #262626;
    margin: 0;
  }
`;

const IconButton = styled.button`
  background: none;
  border: none;
  color: #8c8c8c;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 50%;
  &:hover {
    background-color: #f5f5f5;
  }
`;

const SubText = styled.p`
  font-size: 13px;
  color: #8c8c8c;
  margin: 0 0 24px 0;
  line-height: 1.4;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const InputGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
`;

const Label = styled.label`
  font-size: 14px;
  font-weight: 600;
  color: #262626;
`;

const Input = styled.input`
  padding: 10px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  color: #262626;
  outline: none;

  &:focus {
    border-color: #00a651;
  }

  &::placeholder {
    color: #bfbfbf;
  }
`;

const PriceInputWrapper = styled.div`
  position: relative;
  display: flex;
  align-items: center;

  input {
    width: 100%;
    padding-right: 35px;
  }
`;

const PriceUnit = styled.span`
  position: absolute;
  right: 14px;
  font-size: 14px;
  color: #595959;
`;

const TextArea = styled.textarea`
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  color: #262626;
  outline: none;
  resize: none;
  font-family: inherit;
  line-height: 1.5;

  &:focus {
    border-color: #00a651;
  }

  &::placeholder {
    color: #bfbfbf;
  }
`;

const CharCounter = styled.span`
  font-size: 11px;
  color: #bfbfbf;
  align-self: flex-end;
  margin-top: -4px;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 8px;
`;

const CancelButton = styled.button`
  padding: 10px 20px;
  border-radius: 8px;
  border: 1px solid #d9d9d9;
  background: white;
  color: #595959;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f5f5f5;
  }
`;

const SubmitButton = styled.button`
  padding: 10px 24px;
  border-radius: 8px;
  border: none;
  background: #00a651;
  color: white;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #008c43;
  }

  &:disabled {
    background: #bfbfbf;
    cursor: not-allowed;
  }
`;

function RepresentativeMenuModal({ onClose, onSuccess }) {
  // 백엔드 Request body 규격 세 가지 상태 필드 관리
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [basePrice, setBasePrice] = useState('');
  const [loading, setLoading] = useState(false);

  // 가격 입력 시 숫자만 허용하는 핸들러
  const handlePriceChange = (e) => {
    const value = e.target.value.replace(/[^0-9]/g, '');
    setBasePrice(value);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!name.trim()) return alert('메뉴 이름을 입력해 주세요.');
    if (!basePrice) return alert('기본 가격을 입력해 주세요.');

    setLoading(true);

    // Swagger 스펙에 맞춘 데이터 포맷팅
    const requestBody = {
      name: name.trim(),
      description: description.trim(),
      basePrice: Number(basePrice), // 정수 타입 변환
    };

    try {
      const response = await approvalApi.updateRepresentativeMenu(requestBody);

      if (response.data.success) {
        alert('대표 메뉴가 성공적으로 등록되었습니다.');
        onSuccess(); // 메인 체크리스트 리프레시 및 모달 닫기
      } else {
        alert(`등록 실패: ${response.data.message || '오류가 발생했습니다.'}`);
      }
    } catch (error) {
      console.error('대표 메뉴 저장 에러:', error);
      const serverMessage = error.response?.data?.message;
      alert(
        serverMessage
          ? `에러: ${serverMessage}`
          : '서버 통신 중 에러가 발생했습니다.',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContent onClick={(e) => e.stopPropagation()}>
        {/* 모달 헤더 */}
        <ModalHeader>
          <HeaderTitle>
            <Utensils size={20} color="#00a651" />
            <h3>대표 메뉴 등록/수정</h3>
          </HeaderTitle>
          <IconButton onClick={onClose}>
            <X size={20} />
          </IconButton>
        </ModalHeader>

        <SubText>
          고객들에게 가장 먼저 노출될 상점의 핵심 대표 메뉴를 등록해 주세요.
        </SubText>

        {/* 메인 폼 서브밋 */}
        <Form onSubmit={handleSubmit}>
          {/* 메뉴명 (name) */}
          <InputGroup>
            <Label>메뉴명 *</Label>
            <Input
              type="text"
              placeholder="예) 김치찌개"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={30}
              required
            />
          </InputGroup>

          {/* 기본 가격 (basePrice) */}
          <InputGroup>
            <Label>기본 가격 *</Label>
            <PriceInputWrapper>
              <Input
                type="text"
                placeholder="예) 8000"
                value={basePrice}
                onChange={handlePriceChange}
                required
              />
              <PriceUnit>원</PriceUnit>
            </PriceInputWrapper>
          </InputGroup>

          {/* 메뉴 설명 (description) */}
          <InputGroup>
            <Label>메뉴 설명</Label>
            <TextArea
              rows="3"
              placeholder="예) 직접 끓인 깊은 맛의 김치찌개입니다."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={150}
            />
            <CharCounter>{description.length} / 150자</CharCounter>
          </InputGroup>

          {/* 하단 제어 버턴 */}
          <ButtonGroup>
            <CancelButton type="button" onClick={onClose}>
              취소
            </CancelButton>
            <SubmitButton type="submit" disabled={loading}>
              {loading ? '저장 중...' : '메뉴 저장하기'}
            </SubmitButton>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
}

export default RepresentativeMenuModal;
