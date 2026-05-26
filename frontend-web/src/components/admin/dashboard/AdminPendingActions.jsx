import React from 'react';
import styled from 'styled-components';
import { ChevronRight } from 'lucide-react';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }
  .more {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #52c41a;
    cursor: pointer;
    font-weight: 600;
  }
`;

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ActionItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;

  .left-side {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .icon-avatar {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: #edf5f1;
    color: #2d5a43;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
  }
  .info {
    .title {
      font-size: 13px;
      font-weight: 600;
      color: #262626;
    }
    .desc {
      font-size: 11px;
      color: #bfbfbf;
      margin-top: 2px;
    }
  }
  .right-side {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .action-link {
    font-size: 12px;
    font-weight: 600;
    color: #595959;
    display: flex;
    align-items: center;
    cursor: pointer;
    &:hover {
      color: #2d5a43;
    }
  }
`;

const StatusTag = styled.span`
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 600;
  background-color: ${(props) =>
    props.$type === '긴급' ? '#fff5f5' : '#f5f5f5'};
  color: ${(props) => (props.$type === '긴급' ? '#ff4d4f' : '#595959')};
`;

const pendingData = [
  {
    id: 1,
    icon: '📋',
    title: '사장 가입 승인 12건',
    desc: '수용품 떡케이어뵉 외 11건 · 3시간 전',
    status: '긴급',
  },
  {
    id: 2,
    icon: '🚨',
    title: '신고 처리 5건',
    desc: '사기 의심 1건 · 욕설/비방 2건 · 오늘',
    status: '일반',
  },
  {
    id: 3,
    icon: '💬',
    title: '고객 문의 6건',
    desc: '결제 오류 2건 · 계정 문의 3건 · 1일 전',
    status: '일반',
  },
];

function AdminPendingActions() {
  return (
    <Card>
      <Header>
        <h3>처리 대기 항목</h3>
        <span className="more">
          전체 보기 <ChevronRight size={14} />
        </span>
      </Header>
      <ListContainer>
        {pendingData.map((item) => (
          <ActionItem key={item.id}>
            <div className="left-side">
              <div className="icon-avatar">{item.icon}</div>
              <div className="info">
                <div className="title">{item.title}</div>
                <div className="desc">{item.desc}</div>
              </div>
            </div>
            <div className="right-side">
              <StatusTag $type={item.status}>{item.status}</StatusTag>
              <div className="action-link">
                처리하기 <ChevronRight size={12} />
              </div>
            </div>
          </ActionItem>
        ))}
      </ListContainer>
    </Card>
  );
}

export default AdminPendingActions;
