import React, { useState } from 'react';
import styled from 'styled-components';
import { X, Store } from 'lucide-react';
import { approvalApi } from '../../../../api/owner/ApprovalApi';

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

const Select = styled.select`
  padding: 10px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  color: #262626;
  background-color: white;
  outline: none;
  cursor: pointer;

  &:focus {
    border-color: #00a651;
  }
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

// 프로젝트의 업종 카테고리 정의 (상황에 맞게 ID와 명칭을 매핑하세요)
const CATEGORY_OPTIONS = [
  { id: 1, name: '음식업 / 반찬가게' },
  { id: 2, name: '음식업 / 한식' },
  { id: 3, name: '음식업 / 카페·디저트' },
  { id: 4, name: '음식업 / 일식·돈까스' },
];

function BusinessInfoModal({ onClose, onSuccess }) {
  // 1. 백엔드 Request Body 규격에 맞춘 상태 관리
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);

  // 2. 저장하기 버튼 클릭 핸들러
  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!categoryId) {
      alert('업종 카테고리를 선택해 주세요.');
      return;
    }

    setLoading(true);

    // Swagger 명세 규격 구성
    const requestBody = {
      categoryId: Number(categoryId),
      description: description,
    };

    try {
      const response =
        await approvalApi.updateOwnerStoreBusinessInfo(requestBody);

      if (response.data.success) {
        alert('상점 기본 정보가 저장되었습니다.');
        onSuccess(); // 부모 컴포넌트 리프레시 및 모달 닫기
      } else {
        alert(`저장 실패: ${response.data.message || '오류가 발생했습니다.'}`);
      }
    } catch (error) {
      console.error('상점 정보 저장 에러:', error);
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
            <Store size={20} color="#00a651" />
            <h3>상점 기본 정보 입력</h3>
          </HeaderTitle>
          <IconButton onClick={onClose}>
            <X size={20} />
          </IconButton>
        </ModalHeader>

        <SubText>
          입점 심사를 진행하기 위해 상점의 업종 카테고리와 사장님 상점만의
          특별한 소개글을 작성해 주세요.
        </SubText>

        {/* 입력 폼 */}
        <Form onSubmit={handleSubmit}>
          {/* 업종 카테고리 선택 (categoryId) */}
          <InputGroup>
            <Label>업종 카테고리 *</Label>
            <Select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              required
            >
              <option value="" disabled>
                업종을 선택해 주세요
              </option>
              {CATEGORY_OPTIONS.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name}
                </option>
              ))}
            </Select>
          </InputGroup>

          {/* 상점 설명 입력 (description) */}
          <InputGroup>
            <Label>상점 소개글 *</Label>
            <TextArea
              rows="4"
              placeholder="예) 직접 끓인 김치찌개를 판매하는 동네 가게입니다."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={200}
              required
            />
            <CharCounter>{description.length} / 200자</CharCounter>
          </InputGroup>

          {/* 하단 버튼 바 */}
          <ButtonGroup>
            <CancelButton type="button" onClick={onClose}>
              취소
            </CancelButton>
            <SubmitButton type="submit" disabled={loading}>
              {loading ? '저장 중...' : '정보 저장하기'}
            </SubmitButton>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
}

export default BusinessInfoModal;
