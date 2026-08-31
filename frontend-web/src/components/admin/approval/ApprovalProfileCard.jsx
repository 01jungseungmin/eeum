import React from 'react';
import styled from 'styled-components';

const LeftProfileCard = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);

  .avatar {
    width: 80px;
    height: 80px;
    border-radius: 50%;
    background-color: #e8ebee;
    color: #a0a6b5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 16px;
  }

  h2 {
    margin: 0 0 4px 0;
    font-size: 18px;
    font-weight: 700;
    color: #262626;
  }

  .email-sub {
    font-size: 13px;
    color: #8c8c8c;
    margin-bottom: 16px;
  }

  .status-badge {
    font-size: 12px;
    font-weight: 700;
    padding: 4px 10px;
    border-radius: 20px;
    background: ${(props) =>
      props.$status === 'PENDING'
        ? '#fef7e7'
        : props.$status === 'APPROVED'
          ? '#edf5f1'
          : '#fff5f5'};
    color: ${(props) =>
      props.$status === 'PENDING'
        ? '#e4a11b'
        : props.$status === 'APPROVED'
          ? '#2d5a43'
          : '#ff4d4f'};
    margin-bottom: 32px;
  }

  .info-divider {
    width: 100%;
    height: 1px;
    background-color: #f0f0f0;
    margin-bottom: 20px;
  }

  .meta-item {
    width: 100%;
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    margin-bottom: 12px;

    &:last-child {
      margin-bottom: 0;
    }

    .label {
      color: #8c8c8c;
    }
    .value {
      color: #262626;
      font-weight: 500;
    }
  }
`;

function ApprovalProfileCard({ account }) {
  // 백엔드 데이터 우선 매핑 (Fallback 보장)
  const ownerName = account?.ownerName || account?.name || '이름 없음';
  const email = account?.email || '-';
  const storeName = account?.storeName || account?.nickname || '상점명 없음';
  const status =
    account?.approvalStatus || account?.ownerInfo?.approvalStatus || 'PENDING';
  const createdDate =
    account?.createdAt ||
    account?.reviewRequestedAt ||
    account?.ownerInfo?.createdAt;

  const statusTextMap = {
    PENDING: '승인 대기 중',
    APPROVED: '승인 완료',
    REJECTED: '승인 거부',
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '-';
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
  };

  return (
    <LeftProfileCard $status={status}>
      <div className="avatar">{ownerName.charAt(0)}</div>
      <h2>{ownerName}</h2>
      <div className="email-sub">{email}</div>
      <div className="status-badge">
        {statusTextMap[status] || '승인 대기 중'}
      </div>

      <div className="info-divider" />

      <div className="meta-item">
        <span className="label">신청일</span>
        <span className="value">{formatDate(createdDate)}</span>
      </div>
      <div className="meta-item">
        <span className="label">가게명</span>
        <span className="value">{storeName}</span>
      </div>
    </LeftProfileCard>
  );
}

export default ApprovalProfileCard;
