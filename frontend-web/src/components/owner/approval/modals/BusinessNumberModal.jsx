import { useState } from 'react';
import styled from 'styled-components';
import { X, FileText } from 'lucide-react';
import { accountApi } from '../../../../api/owner/accountApi';

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
`;

const ModalContent = styled.div`
  background: white;
  padding: 24px;
  border-radius: 16px;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
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

const NoticeBox = styled.div`
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 20px;
  font-size: 13px;
  color: #ad6800;
  line-height: 1.5;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const Label = styled.label`
  font-size: 14px;
  font-weight: 600;
  color: #262626;
`;

const Input = styled.input`
  padding: 10px 12px;
  border: 1px solid ${(props) => (props.$invalid ? '#ff4d4f' : '#d9d9d9')};
  border-radius: 8px;
  font-size: 15px;
  letter-spacing: 1px;
  color: #262626;
  outline: none;

  &:focus {
    border-color: ${(props) => (props.$invalid ? '#ff4d4f' : '#00a651')};
  }
`;

const CurrentValue = styled.div`
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 4px;

  strong {
    color: #262626;
    font-weight: 600;
  }
`;

const HelperText = styled.span`
  font-size: 12px;
  color: ${(props) => (props.$error ? '#ff4d4f' : '#8c8c8c')};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 16px;
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

  &:hover {
    background: #008c43;
  }
  &:disabled {
    background: #bfbfbf;
    cursor: not-allowed;
  }
`;

const onlyDigits = (value) => value.replace(/[^0-9]/g, '').slice(0, 10);

function BusinessNumberModal({ currentBusinessNumber, onClose, onSuccess }) {
  // 서버가 사업자번호를 마스킹해서 내려주므로(예: 987-**-43210) 입력창은 비워두고 새 번호를 받는다
  const [businessNumber, setBusinessNumber] = useState('');
  const [loading, setLoading] = useState(false);

  const isValid = /^\d{10}$/.test(businessNumber);
  const showInvalid = businessNumber.length > 0 && !isValid;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isValid) return;

    setLoading(true);
    try {
      const response = await accountApi.updateOwnerAccountInfo({
        businessNumber,
      });
      if (response.data?.success) {
        alert('사업자번호가 수정되었습니다. 입점 심사를 다시 신청해 주세요.');
        onSuccess();
      } else {
        alert(response.data?.message || '사업자번호 수정에 실패했습니다.');
      }
    } catch (error) {
      alert(
        error.response?.data?.error?.message ||
          '사업자번호 수정 중 오류가 발생했습니다.',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContent onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <HeaderTitle>
            <FileText
              size={20}
              color="#00a651"
            />
            <h3>사업자번호 수정</h3>
          </HeaderTitle>
          <IconButton
            type="button"
            onClick={onClose}
          >
            <X size={20} />
          </IconButton>
        </ModalHeader>

        <NoticeBox>
          사업자번호를 변경하면 심사 상태가 초기화됩니다. 변경 후 입점 심사를
          다시 신청해야 하며, 승인이 완료된 뒤에는 변경할 수 없어요.
        </NoticeBox>

        <Form onSubmit={handleSubmit}>
          {currentBusinessNumber && (
            <CurrentValue>
              현재 등록된 번호: <strong>{currentBusinessNumber}</strong>
            </CurrentValue>
          )}
          <Label htmlFor="business-number">새 사업자번호</Label>
          <Input
            id="business-number"
            type="text"
            inputMode="numeric"
            placeholder="숫자 10자리 (하이픈 제외)"
            value={businessNumber}
            onChange={(e) => setBusinessNumber(onlyDigits(e.target.value))}
            $invalid={showInvalid}
            disabled={loading}
            autoFocus
          />
          <HelperText $error={showInvalid}>
            {showInvalid
              ? `숫자 10자리여야 해요 (현재 ${businessNumber.length}자리)`
              : '하이픈(-) 없이 숫자만 입력해 주세요.'}
          </HelperText>

          <ButtonGroup>
            <CancelButton
              type="button"
              onClick={onClose}
              disabled={loading}
            >
              취소
            </CancelButton>
            <SubmitButton
              type="submit"
              disabled={loading || !isValid}
            >
              {loading ? '저장 중...' : '저장'}
            </SubmitButton>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
}

export default BusinessNumberModal;
