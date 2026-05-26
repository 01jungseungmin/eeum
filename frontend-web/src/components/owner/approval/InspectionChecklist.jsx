import React from 'react';
import styled from 'styled-components';
import { CheckCircle2, XCircle } from 'lucide-react';

const InspectionChecklist = ({ status }) => {
  const isRejected = status === 'REJECTED';

  const checklistData = [
    {
      id: 1,
      title: '사업자 등록증 제출 및 인증',
      desc: isRejected ? '서류 내용 불일치 — 재제출 필요' : '인증 완료',
      isDone: !isRejected, // 반려 상태가 아니면 완료로 간주
    },
    { id: 2, title: '대표자 신원 확인', desc: '본인 인증 완료', isDone: true },
    {
      id: 3,
      title: '상점 기본 정보 입력',
      desc: '맛있는 반찬가게 · 서울 마포구',
      isDone: true,
    },
    {
      id: 4,
      title: '대표 메뉴 1개 이상 등록',
      desc: '7개 상품 등록',
      isDone: true,
    },
    { id: 5, title: '영업시간 설정', desc: '월~토 09:00~19:00', isDone: true },
    {
      id: 6,
      title: '정산 계좌 등록',
      desc: '국민은행 ****789012',
      isDone: true,
    },
  ];

  return (
    <Container>
      <Header>
        <Title>입점 심사 체크리스트</Title>
        <SubTitle>모든 항목 완료 후 관리자 검토가 진행됩니다</SubTitle>
      </Header>

      <List>
        {checklistData.map((item) => (
          <Item key={item.id}>
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

const Container = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #f0f0f0;
  margin-bottom: 30px;
`;

const Header = styled.div`
  margin-bottom: 24px;
`;

const Title = styled.h3`
  font-size: 18px;
  font-weight: 700;
  color: #262626;
  margin: 0 0 4px 0;
`;

const SubTitle = styled.p`
  font-size: 14px;
  color: #8c8c8c;
  margin: 0;
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

export default InspectionChecklist;
