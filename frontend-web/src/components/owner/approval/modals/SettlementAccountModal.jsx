import React, { useState } from 'react';
import styled from 'styled-components';
import { X, CreditCard } from 'lucide-react';
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

// 주요 은행 목록 옵션
const BANK_OPTIONS = [
  '국민은행',
  '신한은행',
  '우리은행',
  '하나은행',
  '기업은행',
  '농협은행',
  '카카오뱅크',
  '토스뱅크',
];

// ----------------------------------------------------------------
// 🏁 Component Logic
// ----------------------------------------------------------------
function SettlementAccountModal({ onClose, onSuccess }) {
  // 백엔드 Request Body 스펙 매핑 상태
  const [bankName, setBankName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [accountHolder, setAccountHolder] = useState('');
  const [loading, setLoading] = useState(false);

  // 계좌번호 입력 시 숫자만 허용하는 핸들러
  const handleAccountNumberChange = (e) => {
    const value = e.target.value.replace(/[^0-9]/g, '');
    setAccountNumber(value);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!bankName) return alert('은행을 선택해 주세요.');
    if (!accountNumber.trim()) return alert('계좌번호를 입력해 주세요.');
    if (!accountHolder.trim()) return alert('예금주명을 입력해 주세요.');

    setLoading(true);

    // Swagger 스펙에 맞춘 Request Body 구성
    const requestBody = {
      bankName: bankName,
      accountNumber: accountNumber.trim(),
      accountHolder: accountHolder.trim(),
    };

    try {
      const response =
        await approvalApi.updateOwnerStoreSettlementAccount(requestBody);

      if (response.data.success) {
        alert('정산 계좌 정보가 성공적으로 저장되었습니다.');
        onSuccess(); // 리프레시 후 모달 닫기
      } else {
        alert(`저장 실패: ${response.data.message || '오류가 발생했습니다.'}`);
      }
    } catch (error) {
      console.error('정산 계좌 저장 에러:', error);
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
            <CreditCard size={20} color="#00a651" />
            <h3>정산 계좌 등록/수정</h3>
          </HeaderTitle>
          <IconButton onClick={onClose}>
            <X size={20} />
          </IconButton>
        </ModalHeader>

        <SubText>
          매출 정산 대금을 수령하실 사장님 명의의 계좌 정보를 정확히 입력해
          주세요.
        </SubText>

        <Form onSubmit={handleSubmit}>
          {/* 은행 선택 (bankName) */}
          <InputGroup>
            <Label>은행명 *</Label>
            <Select
              value={bankName}
              onChange={(e) => setBankName(e.target.value)}
              required
            >
              <option value="" disabled>
                은행을 선택해 주세요
              </option>
              {BANK_OPTIONS.map((bank) => (
                <option key={bank} value={bank}>
                  {bank}
                </option>
              ))}
            </Select>
          </InputGroup>

          {/* 계좌번호 입력 (accountNumber) */}
          <InputGroup>
            <Label>계좌번호 *</Label>
            <Input
              type="text"
              placeholder="하이픈(-) 없이 숫자만 입력해 주세요"
              value={accountNumber}
              onChange={handleAccountNumberChange}
              required
            />
          </InputGroup>

          {/* 예금주 입력 (accountHolder) */}
          <InputGroup>
            <Label>예금주명 *</Label>
            <Input
              type="text"
              placeholder="실명 예금주명을 입력해 주세요"
              value={accountHolder}
              onChange={(e) => setAccountHolder(e.target.value)}
              required
            />
          </InputGroup>

          {/* 하단 제어 버튼 */}
          <ButtonGroup>
            <CancelButton type="button" onClick={onClose}>
              취소
            </CancelButton>
            <SubmitButton type="submit" disabled={loading}>
              {loading ? '저장 중...' : '계좌 저장하기'}
            </SubmitButton>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
}

export default SettlementAccountModal;
