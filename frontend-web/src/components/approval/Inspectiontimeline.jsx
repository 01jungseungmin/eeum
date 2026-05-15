import React from 'react';
import styled from 'styled-components';

const InspectionTimeline = ({ status }) => {
  const isRejected = status === 'REJECTED';

  const timelineData = [
    {
      title: '가입 신청',
      date: '2024-04-25 09:15',
      color: '#52c41a',
      active: true,
    },
    {
      title: '기본 정보 입력 완료',
      date: '2024-04-25 09:20',
      color: '#52c41a',
      active: true,
    },
    {
      title: '사업자 등록증 제출 완료',
      date: '2024-04-25 11:00',
      color: '#52c41a',
      active: true,
    },
    {
      title: '관리자 서류 검토 시작',
      date: '2024-04-26 10:00',
      color: '#52c41a',
      active: true,
    },
    {
      title: isRejected ? '승인 반려 — 서류 보완 요청' : '서류 검토 완료',
      date: '2024-04-26 14:30',
      color: isRejected ? '#ff4d4f' : '#52c41a',
      active: true,
      isHighlight: isRejected,
    },
    {
      title: '사업자 등록증 재제출 대기 중',
      date: '—',
      color: isRejected ? '#fa8c16' : '#d9d9d9',
      active: isRejected,
    },
    { title: '최종 승인', date: '—', color: '#d9d9d9', active: false },
  ];

  return (
    <Container>
      <Title>심사 진행 이력</Title>
      <TimelineList>
        {timelineData.map((item, index) => (
          <TimelineItem key={index}>
            <LineSection>
              <Dot $color={item.color} />
              {index !== timelineData.length - 1 && <Line />}
            </LineSection>
            <ContentSection>
              <ItemTitle $isHighlight={item.isHighlight} $active={item.active}>
                {item.title}
              </ItemTitle>
              <ItemDate>{item.date}</ItemDate>
            </ContentSection>
          </TimelineItem>
        ))}
      </TimelineList>
    </Container>
  );
};

const Container = styled.div`
  background: white;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #f0f0f0;
`;

const Title = styled.h3`
  font-size: 18px;
  font-weight: 700;
  color: #262626;
  margin: 0 0 24px 0;
`;

const TimelineList = styled.div`
  display: flex;
  flex-direction: column;
`;

const TimelineItem = styled.div`
  display: flex;
  gap: 16px;
  min-height: 70px;
`;

const LineSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 12px;
`;

const Dot = styled.div`
  width: 12px;
  height: 12px;
  background-color: ${(props) => props.$color};
  border-radius: 50%;
  z-index: 1;
`;

const Line = styled.div`
  width: 2px;
  flex-grow: 1;
  background-color: #f0f0f0;
  margin: 4px 0;
`;

const ContentSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 20px;
`;

const ItemTitle = styled.h5`
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: ${(props) => {
    if (props.$isHighlight) return '#ff4d4f';
    if (props.$active) return '#262626';
    return '#bfbfbf';
  }};
`;

const ItemDate = styled.span`
  font-size: 13px;
  color: #8c8c8c;
`;

export default InspectionTimeline;
