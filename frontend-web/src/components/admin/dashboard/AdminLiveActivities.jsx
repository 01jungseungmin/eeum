import React from 'react';
import styled from 'styled-components';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
`;

const Header = styled.div`
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }
`;

const TimelineContainer = styled.div`
  display: flex;
  flex-direction: column;
`;

const TimelineRow = styled.div`
  display: flex;
  gap: 14px;
  padding-bottom: 20px;
  position: relative;
  &:last-child {
    padding-bottom: 0;
  }
`;

const TimelineDot = styled.div`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2d5a43;
  margin-top: 5px;
  position: relative;
  z-index: 2;
  &::after {
    content: '';
    position: absolute;
    top: 8px;
    left: 3px;
    width: 2px;
    height: 48px;
    background: #f0f0f0;
    z-index: 1;
  }
  ${TimelineRow}:last-child &::after {
    display: none;
  }
`;

const TimelineContent = styled.div`
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  .text-side {
    h4 {
      font-size: 13px;
      font-weight: 600;
      margin: 0 0 2px 0;
      color: #262626;
    }
    p {
      font-size: 12px;
      color: #8c8c8c;
      margin: 0;
    }
  }
  .time-text {
    font-size: 11px;
    color: #bfbfbf;
  }
`;

const activities = [
  { id: 1, title: '새 회원 가입', desc: '강남구(신규 유저)', time: '방금 전' },
  { id: 2, title: '신규 가게 등록', desc: '수용품 떡케이어뵉', time: '5분 전' },
  { id: 3, title: '거래 완료', desc: '₩47,500 결제 확인', time: '8분 전' },
  {
    id: 4,
    title: '플랫폼 신고 접수',
    desc: '사기 의심 게시글 필터링',
    time: '12분 전',
  },
];

function AdminLiveActivities() {
  return (
    <Card>
      <Header>
        <h3>실시간 활동</h3>
      </Header>
      <TimelineContainer>
        {activities.map((act) => (
          <TimelineRow key={act.id}>
            <TimelineDot />
            <TimelineContent>
              <div className="text-side">
                <h4>{act.title}</h4>
                <p>{act.desc}</p>
              </div>
              <span className="time-text">{act.time}</span>
            </TimelineContent>
          </TimelineRow>
        ))}
      </TimelineContainer>
    </Card>
  );
}

export default AdminLiveActivities;
