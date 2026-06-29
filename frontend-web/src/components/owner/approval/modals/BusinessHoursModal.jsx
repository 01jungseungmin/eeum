import React, { useState } from 'react';
import styled from 'styled-components';
import { X, Clock } from 'lucide-react'; // 깔끔한 아이콘 사용
import { approvalApi } from '../../../../api/owner/ApprovalApi';

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4); // 딤드 처리 색상 투명도 조절
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
  max-width: 520px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  max-height: 85vh;
  overflow-y: auto; // 내용이 길어지면 내부 스크롤 허용
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
  margin: 0 0 20px 0;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
`;

const HoursList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
`;

const Row = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid ${(props) => (props.$isClosed ? '#f0f0f0' : '#e8e8e8')};
  background-color: ${(props) => (props.$isClosed ? '#fafafa' : '#fff')};
  transition: all 0.2s;
`;

const DayLabel = styled.span`
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  width: 50px;
`;

const CheckboxLabel = styled.label`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #595959;
  cursor: pointer;
  user-select: none;

  input {
    cursor: pointer;
    accent-color: #00a651; // 체크박스 초록색 통일
  }
`;

const TimeInputGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
`;

const TimeInput = styled.input`
  padding: 5px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 13px;
  color: #262626;
  font-family: inherit;

  &:focus {
    outline: none;
    border-color: #00a651;
  }

  &:disabled {
    background: #f5f5f5;
    color: #bfbfbf;
    border-color: #d9d9d9;
  }
`;

const Divider = styled.span`
  color: #8c8c8c;
  font-size: 13px;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
  justify-content: flex-end;
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

const DAY_LABELS = {
  MONDAY: '월요일',
  TUESDAY: '화요일',
  WEDNESDAY: '수요일',
  THURSDAY: '목요일',
  FRIDAY: '금요일',
  SATURDAY: '토요일',
  SUNDAY: '일요일',
};

function BusinessHoursModal({ onClose, onSuccess }) {
  // 1. 초기 폼 상태 (백엔드 Request Format과 동일)
  const [businessHours, setBusinessHours] = useState([
    {
      dayOfWeek: 'MONDAY',
      closed: false,
      openTime: '09:00',
      closeTime: '19:00',
    },
    {
      dayOfWeek: 'TUESDAY',
      closed: false,
      openTime: '09:00',
      closeTime: '19:00',
    },
    {
      dayOfWeek: 'WEDNESDAY',
      closed: false,
      openTime: '09:00',
      closeTime: '19:00',
    },
    {
      dayOfWeek: 'THURSDAY',
      closed: false,
      openTime: '09:00',
      closeTime: '19:00',
    },
    {
      dayOfWeek: 'FRIDAY',
      closed: false,
      openTime: '09:00',
      closeTime: '19:00',
    },
    {
      dayOfWeek: 'SATURDAY',
      closed: false,
      openTime: '10:00',
      closeTime: '18:00',
    },
    { dayOfWeek: 'SUNDAY', closed: true, openTime: null, closeTime: null },
  ]);

  const [loading, setLoading] = useState(false);

  // 2. 시간 선택 핸들러
  const handleTimeChange = (index, field, value) => {
    const updatedHours = [...businessHours];
    updatedHours[index][field] = value;
    setBusinessHours(updatedHours);
  };

  // 3. 정기 휴무 체크박스 핸들러 (체크 시 시간 null 처리)
  const handleClosedToggle = (index) => {
    const updatedHours = [...businessHours];
    const isClosed = !updatedHours[index].closed;

    updatedHours[index].closed = isClosed;
    if (isClosed) {
      updatedHours[index].openTime = null;
      updatedHours[index].closeTime = null;
    } else {
      updatedHours[index].openTime = '09:00'; // 휴무 해제 시 기본 오전 9시 세팅
      updatedHours[index].closeTime = '19:00'; // 기본 오후 7시 세팅
    }
    setBusinessHours(updatedHours);
  };

  // 4. 저장하기 버튼 클릭 핸들러
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      // API 전송 (Body 규격 {"businessHours": [...]})
      const response = await approvalApi.updateOwnerStoreBusinessHours({
        businessHours,
      });

      if (response.data.success) {
        alert('영업시간이 정상적으로 저장되었습니다.');
        onSuccess(); // 부모 컴포넌트 리프레시 및 모달 닫기
      } else {
        alert(`저장 실패: ${response.data.message || '오류가 발생했습니다.'}`);
      }
    } catch (error) {
      console.error('영업시간 저장 에러:', error);
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
            <Clock size={20} color="#00a651" />
            <h3>영업시간 수정</h3>
          </HeaderTitle>
          <IconButton onClick={onClose}>
            <X size={20} />
          </IconButton>
        </ModalHeader>

        <SubText>
          입점 심사를 진행하기 위해 요일별 정확한 영업시간을 입력해 주세요.
        </SubText>

        {/* 모달 바디 (입력 폼) */}
        <Form onSubmit={handleSubmit}>
          <HoursList>
            {businessHours.map((item, index) => (
              <Row key={item.dayOfWeek} $isClosed={item.closed}>
                <DayLabel>{DAY_LABELS[item.dayOfWeek]}</DayLabel>

                {/* 정기 휴무 체크박스 */}
                <CheckboxLabel>
                  <input
                    type="checkbox"
                    checked={item.closed}
                    onChange={() => handleClosedToggle(index)}
                  />
                  <span>정기 휴무</span>
                </CheckboxLabel>

                {/* 시간 범위 셀렉트 가이드 */}
                <TimeInputGroup>
                  <TimeInput
                    type="time"
                    value={item.openTime || ''}
                    disabled={item.closed}
                    onChange={(e) =>
                      handleTimeChange(index, 'openTime', e.target.value)
                    }
                    required={!item.closed}
                  />
                  <Divider>~</Divider>
                  <TimeInput
                    type="time"
                    value={item.closeTime || ''}
                    disabled={item.closed}
                    onChange={(e) =>
                      handleTimeChange(index, 'closeTime', e.target.value)
                    }
                    required={!item.closed}
                  />
                </TimeInputGroup>
              </Row>
            ))}
          </HoursList>

          {/* 모달 하단 버튼 바 */}
          <ButtonGroup>
            <CancelButton type="button" onClick={onClose}>
              취소
            </CancelButton>
            <SubmitButton type="submit" disabled={loading}>
              {loading ? '저장 중...' : '변경사항 저장'}
            </SubmitButton>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
}

export default BusinessHoursModal;
