import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { Pencil, X, Save } from 'lucide-react';

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

/* 💡 [신규] 요일별 시간 아이템들을 2줄(2열)로 배치하기 위한 그리드 컨테이너 */
const HoursGridContainer = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr; /* PC/기본창 크기일 때는 양 옆 2열 정렬 */
  gap: x;
  column-gap: 24px; /* 좌우 열 사이의 간격 */
  row-gap: 12px; /* 상하 행 사이의 간격 */
  margin-top: 12px;

  /* 💡 창의 크기가 작아지면(태블릿, 모바일 등 미디어쿼리 범위) 한 줄로 유연하게 보임 */
  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const HourRow = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  background: #fafafa;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
`;

const HolidayToggleBtn = styled.button`
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid ${(props) => (props.$isHoliday ? '#ff4d4f' : '#2d5a43')};
  background: white;
  color: ${(props) => (props.$isHoliday ? '#ff4d4f' : '#2d5a43')};
  font-size: 12px;
  font-weight: 600;
  cursor: ${(props) => (props.disabled ? 'not-allowed' : 'pointer')};
  margin-left: auto; /* 우측 끝으로 깔끔하게 밀어주기 */
`;

function StoreInfoForm({ storeInfo, onSave }) {
  const mountaineerHoursFallback = [
    { day: '월', start: '09:00', end: '19:00', isHoliday: false },
    { day: '화', start: '09:00', end: '19:00', isHoliday: false },
    { day: '수', start: '09:00', end: '19:00', isHoliday: false },
    { day: '목', start: '09:00', end: '19:00', isHoliday: false },
    { day: '금', start: '09:00', end: '19:00', isHoliday: false },
    { day: '토', start: '09:00', end: '19:00', isHoliday: false },
    { day: '일', start: '09:00', end: '19:00', isHoliday: true },
  ];

  const createInitialFormData = (info) => {
    if (!info)
      return {
        name: '',
        categoryName: '',
        description: '',
        phone: '',
        address: '',
        operatingHours: mountaineerHoursFallback,
      };
    const copy = JSON.parse(JSON.stringify(info));
    if (!copy.operatingHours) {
      copy.operatingHours = mountaineerHoursFallback;
    }
    return copy;
  };

  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState(() =>
    createInitialFormData(storeInfo),
  );

  useEffect(() => {
    if (!isEditing && storeInfo) {
      setFormData(createInitialFormData(storeInfo));
    }
  }, [storeInfo, isEditing]);

  if (!formData) {
    return <Card>데이터를 불러오는 중입니다...</Card>;
  }

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

  const handleToggleHoliday = (index) => {
    if (!isEditing) return;
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

      <Field>
        <Label>🕒 영업 시간 설정</Label>
        <HoursGridContainer>
          {formData.operatingHours?.map((item, idx) => (
            <HourRow key={item.day || idx}>
              <span style={{ width: '20px', fontWeight: '700', color: '#333' }}>
                {item.day}
              </span>
              <Input
                type="text"
                style={{
                  padding: '6px 4px',
                  width: '60px',
                  textAlign: 'center',
                  fontSize: '13px',
                }}
                disabled={!isEditing || item.isHoliday}
                value={item.start || ''}
                onChange={(e) => {
                  const updated = [...formData.operatingHours];
                  updated[idx].start = e.target.value;
                  setFormData({ ...formData, operatingHours: updated });
                }}
              />
              <span style={{ color: '#aaa' }}>~</span>
              <Input
                type="text"
                style={{
                  padding: '6px 4px',
                  width: '60px',
                  textAlign: 'center',
                  fontSize: '13px',
                }}
                disabled={!isEditing || item.isHoliday}
                value={item.end || ''}
                onChange={(e) => {
                  const updated = [...formData.operatingHours];
                  updated[idx].end = e.target.value;
                  setFormData({ ...formData, operatingHours: updated });
                }}
              />
              <HolidayToggleBtn
                type="button"
                disabled={!isEditing}
                $isHoliday={item.isHoliday}
                onClick={() => handleToggleHoliday(idx)}
              >
                {item.isHoliday ? '휴무' : '영업'}
              </HolidayToggleBtn>
            </HourRow>
          ))}
        </HoursGridContainer>
      </Field>
    </Card>
  );
}

export default StoreInfoForm;
