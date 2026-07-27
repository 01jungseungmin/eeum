import React, { useState } from 'react';
import styled from 'styled-components';
import { reportApi } from '../../../api/owner/reportApi'; // API 경로에 맞춰 수정해주세요

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
`;

const ModalContainer = styled.div`
  background: white;
  border-radius: 16px;
  width: 440px;
  padding: 32px;
  position: relative;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
`;

const CloseButton = styled.button`
  position: absolute;
  top: 20px;
  right: 20px;
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #9ca3af;
`;

const Title = styled.h2`
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
`;

const Notice = styled.p`
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  margin-bottom: 20px;
`;

const ReasonList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
`;

const ReasonItem = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid ${(props) => (props.$active ? '#ffedd5' : '#e5e7eb')};
  background-color: ${(props) => (props.$active ? '#fff7ed' : '#ffffff')};
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
`;

const RadioCircle = styled.div`
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid ${(props) => (props.$active ? '#f97316' : '#9ca3af')};
  background: ${(props) => (props.$active ? '#f97316' : 'transparent')};
  position: relative;

  &::after {
    content: '';
    position: absolute;
    inset: 4px;
    background: white;
    border-radius: 50%;
    display: ${(props) => (props.$active ? 'block' : 'none')};
  }
`;

const StyledTextArea = styled.textarea`
  width: 100%;
  height: 80px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  resize: none;
  font-size: 13px;
  outline: none;
  margin-bottom: 24px;
  box-sizing: border-box;
  &:focus {
    border-color: #f97316;
  }
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
`;

const CancelButton = styled.button`
  flex: 1;
  padding: 14px;
  border: 1px solid #e5e7eb;
  background: white;
  border-radius: 8px;
  cursor: pointer;
`;

const SubmitButton = styled.button`
  flex: 1;
  padding: 14px;
  background: ${(props) => (props.disabled ? '#f3f4f6' : '#ffedd5')};
  color: ${(props) => (props.disabled ? '#9ca3af' : '#ea580c')};
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: ${(props) => (props.disabled ? 'not-allowed' : 'pointer')};
  &:hover {
    background: ${(props) => (props.disabled ? '#f3f4f6' : '#fed7aa')};
  }
`;

// 백엔드 reason Enum과 매핑되는 리스트
const REPORT_REASONS = [
  { label: '악의적/허위 리뷰', code: 'FALSE_INFORMATION' },
  { label: '욕설/비방/모욕', code: 'ABUSE' },
  { label: '스팸/광고/영업 방해', code: 'SPAM' },
  { label: '사기/기만', code: 'FRAUD' },
  { label: '부적절한 콘텐츠', code: 'INAPPROPRIATE_CONTENT' },
  { label: '기타 사유', code: 'ETC' },
];

export default function ReportModal({ reviewId, onClose }) {
  const [selectedReasonCode, setSelectedReasonCode] = useState('');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!selectedReasonCode) {
      alert('신고 사유를 선택해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);

      // POST /reports 요청 바디
      const payload = {
        targetType: 'STORE_REVIEW', // 리뷰 신고이므로 STORE_REVIEW 고정
        targetId: Number(reviewId),
        reason: selectedReasonCode,
        content: description,
      };

      const response = await reportApi.createReport(payload);

      if (response.data && response.data.success) {
        alert('신고가 성공적으로 접수되었습니다.');
        onClose();
      } else {
        alert(response.data?.message || '신고 접수에 실패했습니다.');
      }
    } catch (error) {
      console.error('신고 접수 중 오류:', error);
      const errorMessage =
        error.response?.data?.message || '신고 접수 중 오류가 발생했습니다.';
      alert(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Overlay>
      <ModalContainer>
        <CloseButton onClick={onClose}>&times;</CloseButton>
        <Title>악성 리뷰 신고</Title>
        <Notice>
          이음 운영팀에서 검토 후 처리 결과를 안내드립니다. 동일한 대상에 대한
          중복 신고는 불가합니다.
        </Notice>

        <ReasonList>
          {REPORT_REASONS.map((item) => (
            <ReasonItem
              key={item.code}
              $active={selectedReasonCode === item.code}
              onClick={() => setSelectedReasonCode(item.code)}
            >
              <RadioCircle $active={selectedReasonCode === item.code} />
              {item.label}
            </ReasonItem>
          ))}
        </ReasonList>

        <StyledTextArea
          placeholder="추가 설명 (선택 사항)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <ButtonGroup>
          <CancelButton onClick={onClose} disabled={isSubmitting}>
            취소
          </CancelButton>
          <SubmitButton onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? '접수 중...' : '신고 접수'}
          </SubmitButton>
        </ButtonGroup>
      </ModalContainer>
    </Overlay>
  );
}
