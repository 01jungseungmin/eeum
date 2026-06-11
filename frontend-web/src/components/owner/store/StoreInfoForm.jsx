import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Pencil, X, Save } from 'lucide-react';
import StoreHoursForm from './StoreHoursForm'; // 분리된 영업시간 컴포넌트

const Card = styled.div`
  background: white;
  border-radius: 16px;
  border: 1px solid #e8e8e8;
  padding: 24px;
`;

const FormHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 16px;
  margin-bottom: 20px;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 8px;
`;

const GridGroup = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: #444;
`;

const Input = styled.input`
  padding: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  background: ${(props) => (props.disabled ? '#f5f5f5' : 'white')};
`;

const TextArea = styled.textarea`
  padding: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  height: 80px;
  resize: none;
  background: ${(props) => (props.disabled ? '#f5f5f5' : 'white')};
`;

const BaseButton = styled.button`
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  transition: all 0.15s;
`;

const EditButton = styled(BaseButton)`
  background: white;
  color: #2d5a43;
  border: 1px solid #2d5a43;
  &:hover {
    background: #f4faf6;
  }
`;

const CancelButton = styled(BaseButton)`
  background: white;
  color: #555;
  border: 1px solid #d9d9d9;
  &:hover {
    background: #f5f5f5;
  }
`;

const SaveButton = styled(BaseButton)`
  background: #2d5a43;
  color: white;
  border: 1px solid #2d5a43;
  &:hover {
    background: #1e3d2d;
  }
`;

function StoreInfoForm({ storeInfo, onSave }) {
  // 💡 백엔드에 데이터가 없거나 조회에 실패했을 때만 띄워줄 최소한의 안전장치(Fallback)
  const mountaineerHoursFallback = [
    { day: '월', start: '09:00', end: '19:00', isHoliday: false },
    { day: '화', start: '09:00', end: '19:00', isHoliday: false },
    { day: '수', start: '09:00', end: '19:00', isHoliday: false },
    { day: '목', start: '09:00', end: '19:00', isHoliday: false },
    { day: '금', start: '09:00', end: '19:00', isHoliday: false },
    { day: '토', start: '09:00', end: '19:00', isHoliday: false },
    { day: '일', start: '09:00', end: '19:00', isHoliday: true },
  ];

  // 💡 데이터 초기화 헬퍼 함수 수정
  const createInitialFormData = (info) => {
    if (!info) {
      return {
        name: '',
        categoryName: '',
        description: '',
        phone: '',
        address: '',
        operatingHours: mountaineerHoursFallback,
      };
    }

    // 부모로부터 받아온 원본 데이터 깊은 복사
    const copy = JSON.parse(JSON.stringify(info));

    // 💡 중요: 부모가 넘겨준 operatingHours(즉 businessHours) 배열이 비어있지 않다면 그것을 우선 사용!
    // 만약 완전히 비어있을 때만(배열 길이가 0일 때만) 기존 폴백 데이터를 심어줍니다.
    if (!copy.operatingHours || copy.operatingHours.length === 0) {
      copy.operatingHours = mountaineerHoursFallback;
    }

    return copy;
  };

  const [isEditing, setIsEditing] = useState(false);

  // 최초 렌더링 시점에 초기 데이터 바인딩
  const [formData, setFormData] = useState(() =>
    createInitialFormData(storeInfo),
  );

  // 💡 부모 컴포넌트(StorePage)에서 비동기로 API 조회가 완료되어 데이터가 변경되면 폼 상태 동기화
  useEffect(() => {
    if (!isEditing && storeInfo) {
      setFormData(createInitialFormData(storeInfo));
    }
  }, [storeInfo, isEditing]);

  const handleCancel = () => {
    if (
      window.confirm(
        '변경 사항을 취소하시겠습니까? 입력한 내용이 초기화됩니다.',
      )
    ) {
      setFormData(createInitialFormData(storeInfo));
      setIsEditing(false);
    }
  };

  const handleSaveSubmit = () => {
    if (onSave) onSave(formData);
    setIsEditing(false);
  };

  // 영업시간 텍스트(시작/종료) 변경 핸들러
  const handleHoursChange = (index, field, value) => {
    const updatedHours = [...(formData.operatingHours || [])];
    if (updatedHours[index]) {
      updatedHours[index][field] = value;
      setFormData({ ...formData, operatingHours: updatedHours });
    }
  };

  // 영업시간 휴무 여부 토글 핸들러
  const handleToggleHoliday = (index) => {
    const updatedHours = [...(formData.operatingHours || [])];
    if (updatedHours[index]) {
      updatedHours[index].isHoliday = !updatedHours[index].isHoliday;
      setFormData({ ...formData, operatingHours: updatedHours });
    }
  };

  return (
    <Card>
      <FormHeader>
        <h3 style={{ margin: 0, fontSize: '18px' }}>상점 정보</h3>
        <ButtonGroup>
          {isEditing ? (
            <>
              <CancelButton onClick={handleCancel}>
                <X size={14} /> 취소
              </CancelButton>
              <SaveButton onClick={handleSaveSubmit}>
                <Save size={14} /> 저장하기
              </SaveButton>
            </>
          ) : (
            <EditButton onClick={() => setIsEditing(true)}>
              <Pencil size={14} /> 정보 수정
            </EditButton>
          )}
        </ButtonGroup>
      </FormHeader>

      <GridGroup>
        <Field>
          <Label>상점명 *</Label>
          <Input
            disabled={!isEditing}
            value={formData.name || ''}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />
        </Field>
        <Field>
          <Label>카테고리 *</Label>
          <Input
            disabled={!isEditing}
            value={formData.categoryName || formData.category || ''}
            onChange={(e) =>
              setFormData({ ...formData, categoryName: e.target.value })
            }
          />
        </Field>
      </GridGroup>

      <Field>
        <Label>상점 소개</Label>
        <TextArea
          disabled={!isEditing}
          value={formData.description || ''}
          onChange={(e) =>
            setFormData({ ...formData, description: e.target.value })
          }
        />
      </Field>

      <GridGroup>
        <Field>
          <Label>전화번호</Label>
          <Input
            disabled={!isEditing}
            value={formData.phone || ''}
            onChange={(e) =>
              setFormData({ ...formData, phone: e.target.value })
            }
          />
        </Field>
        <Field>
          <Label>주소</Label>
          <Input
            disabled={!isEditing}
            value={formData.address || ''}
            onChange={(e) =>
              setFormData({ ...formData, address: e.target.value })
            }
          />
        </Field>
      </GridGroup>

      {/* 영업 시간 지정 서브 섹션 */}
      <Field style={{ marginBottom: 0 }}>
        <Label>🕒 영업 시간 설정</Label>
        <StoreHoursForm
          isEditing={isEditing}
          operatingHours={formData.operatingHours}
          onHoursChange={handleHoursChange}
          onToggleHoliday={handleToggleHoliday}
        />
      </Field>
    </Card>
  );
}

export default StoreInfoForm;
