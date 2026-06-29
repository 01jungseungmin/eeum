import React from 'react';
import styled from 'styled-components';
import { CheckCircle2, XCircle } from 'lucide-react';

const Container = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #f0f0f0;
  margin-bottom: 30px;
`;

const InnerHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start; /* 텍스트와 버튼 높이가 달라도 상단 라인이 칼같이 맞도록 설정 */
  width: 100%;
  margin-bottom: 28px;
`;

const TitleSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;

  h3 {
    font-size: 20px;
    font-weight: 700;
    color: #1a1a1a;
    margin: 0;
  }

  p {
    font-size: 13px;
    color: #8c8c8c;
    margin: 0;
  }
`;

const List = styled.div`
  display: flex;
  flex-direction: column;
`;

const Item = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid #fafafa;

  &:last-child {
    border-bottom: none;
  }
`;

const LeftSection = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`;

const IconWrapper = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${(props) => (props.$isDone ? '#b7eb8f' : '#ffa39e')};

  /* 배경 원형 효과 */
  background: ${(props) => (props.$isDone ? '#f6ffed' : '#fff1f0')};
  width: 44px;
  height: 44px;
  border-radius: 50%;
`;

const TextGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const ItemTitle = styled.h5`
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: ${(props) => (props.$isError ? '#ff4d4f' : '#262626')};
`;

const ItemDesc = styled.p`
  font-size: 13px;
  margin: 0;
  color: ${(props) => (props.$isError ? '#ff4d4f' : '#8c8c8c')};
`;

const StatusBadge = styled.div`
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  background: ${(props) => (props.$isDone ? '#e6f7ff' : '#fff1f0')};
  color: ${(props) => (props.$isDone ? '#1890ff' : '#cf1322')};
`;

const InspectionChecklist = ({ checklist, onItemClick, children }) => {
  const checklistData = [
    {
      id: 1,
      title: '사업자 등록증 제출 및 인증',
      // 백엔드에서 rejectionReason이 존재하고 businessVerified가 false라면 재제출 필요로 표시
      desc: checklist.businessVerified
        ? '인증 완료'
        : checklist.rejectionReason
          ? '서류 내용 불일치 — 재제출 필요'
          : '인증 진행 중',
      isDone: checklist.businessVerified,
      modalType: null,
    },
    {
      id: 2,
      title: '대표자 신원 확인',
      desc: '본인 인증 완료',
      isDone: true,
      modalType: null, // 클릭 비활성화
    },
    {
      id: 3,
      title: '상점 기본 정보 입력',
      desc: checklist.storeInfoCompleted
        ? '등록 완료'
        : '상점 정보를 입력해 주세요.',
      isDone: checklist.storeInfoCompleted,
      modalType: 'STORE_INFO',
    },
    {
      id: 4,
      title: '대표 메뉴 1개 이상 등록',
      desc: checklist.menuRegistered
        ? '등록 완료'
        : '대표 메뉴를 등록해 주세요.',
      isDone: checklist.menuRegistered,
      modalType: 'MENU',
    },
    {
      id: 5,
      title: '영업시간 설정',
      desc: checklist.businessHoursSet
        ? '설정 완료'
        : '영업시간을 등록해 주세요.',
      isDone: checklist.businessHoursSet,
      modalType: 'HOURS',
    },
    {
      id: 6,
      title: '정산 계좌 등록',
      desc: checklist.settlementAccountRegistered
        ? '등록 완료'
        : '정산 계좌를 등록해 주세요.',
      isDone: checklist.settlementAccountRegistered,
      modalType: 'ACCOUNT',
    },
  ];

  return (
    <Container>
      <InnerHeader>
        {/* 왼쪽: 타이틀과 보조 설명 텍스트 */}
        <TitleSection>
          <h3>입점 심사 체크리스트</h3>
          <p>모든 항목 완료 후 관리자 검토가 진행됩니다</p>
        </TitleSection>

        {/* 오른쪽: ApprovalStatus에서 주입해 준 버튼 (<ApplySubmitButton />) */}
        <div>{children}</div>
      </InnerHeader>

      <List>
        {checklistData.map((item) => (
          <Item
            key={item.id}
            onClick={() => item.modalType && onItemClick(item.modalType)}
            style={{ cursor: item.modalType ? 'pointer' : 'default' }}
          >
            <LeftSection>
              <IconWrapper $isDone={item.isDone}>
                {item.isDone ? (
                  <CheckCircle2 size={24} />
                ) : (
                  <XCircle size={24} />
                )}
              </IconWrapper>
              <TextGroup>
                <ItemTitle $isError={!item.isDone}>{item.title}</ItemTitle>
                <ItemDesc $isError={!item.isDone}>{item.desc}</ItemDesc>
              </TextGroup>
            </LeftSection>
            <StatusBadge $isDone={item.isDone}>
              {item.isDone ? '완료' : '미완료'}
            </StatusBadge>
          </Item>
        ))}
      </List>
    </Container>
  );
};

export default InspectionChecklist;
