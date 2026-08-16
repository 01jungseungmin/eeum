import React from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

const ApprovalRowItem = styled.div`
  background: white;
  border: 1px solid ${(props) => (props.$isSelected ? '#2d5a43' : '#e8e8e8')};
  border-radius: 8px;
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease-in-out;

  &:hover {
    border-color: #2d5a43;
    box-shadow: 0 2px 8px rgba(45, 90, 67, 0.04);
  }

  .store-info-zone {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .title-row {
      display: flex;
      align-items: center;
      gap: 8px;
      h3 {
        margin: 0;
        font-size: 15px;
        font-weight: 700;
        color: #262626;
      }
    }

    .details-row {
      display: flex;
      gap: 32px;
      font-size: 13px;
      color: #7f7f7f;
      strong {
        color: #262626;
        font-weight: 500;
        margin-right: 6px;
      }
    }
  }

  .action-zone {
    display: flex;
    gap: 8px;

    button {
      padding: 7px 16px;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      border: 1px solid transparent;
      transition: all 0.15s ease;
    }
    .btn-approve {
      background: #2d5a43;
      color: white;
      &:hover {
        background: #1f3f2f;
      }
    }
    .btn-reject {
      background: white;
      color: #ff4d4f;
      border-color: #ffccc7;
      &:hover {
        background: #fff1f0;
      }
    }
  }
`;

const StatusBadge = styled.span`
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: ${(props) =>
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
`;

function ApprovalItem({ account, isSelected, onApprove, onReject }) {
  const navigate = useNavigate();

  // 실제 전달되는 백엔드 데이터 필드 직접 추출
  const {
    ownerInfoId,
    accountId,
    storeName,
    ownerName,
    email,
    phone,
    createdAt,
    reviewRequestedAt,
    approvalStatus,
    // 호환용 fallback
    ownerInfo,
    name,
    nickname,
  } = account || {};

  // 바인딩 데이터 정리
  const displayStoreName =
    storeName || nickname || ownerInfo?.storeName || '상점명 없음';
  const displayOwnerName = ownerName || name || ownerInfo?.ownerName || '-';
  const displayPhone = phone || ownerInfo?.phone || '-';
  const displayStatus =
    approvalStatus || ownerInfo?.approvalStatus || 'PENDING';
  const displayCreatedDate =
    createdAt || reviewRequestedAt || ownerInfo?.createdAt;

  // 상세 이동용 ID
  const targetId = ownerInfoId || accountId || ownerInfo?.ownerInfoId;

  const statusMap = {
    PENDING: '대기중',
    REJECTED: '반려',
    APPROVED: '승인완료',
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '-';
    return `${date.getFullYear()}년 ${String(date.getMonth() + 1).padStart(2, '0')}월 ${String(date.getDate()).padStart(2, '0')}일`;
  };

  const handleItemClick = () => {
    if (!targetId) {
      alert('유효한 신청 ID를 찾을 수 없습니다.');
      return;
    }
    navigate(`/admin/approval/${targetId}`);
  };

  return (
    <ApprovalRowItem
      $isSelected={isSelected}
      onClick={handleItemClick}
    >
      <div className="store-info-zone">
        <div className="title-row">
          <h3>{displayStoreName}</h3>
          <StatusBadge $status={displayStatus}>
            {statusMap[displayStatus] || '대기중'}
          </StatusBadge>
        </div>
        <div className="details-row">
          <span>
            <strong>대표자명:</strong>
            {displayOwnerName}
          </span>
          <span>
            <strong>이메일:</strong>
            {email || '-'}
          </span>
          <span>
            <strong>연락처:</strong>
            {displayPhone}
          </span>
          <span>
            <strong>신청일:</strong>
            {formatDate(displayCreatedDate)}
          </span>
        </div>
      </div>

      <div className="action-zone">
        <button
          className="btn-approve"
          onClick={(e) => {
            e.stopPropagation();
            onApprove?.(account);
          }}
        >
          승인
        </button>
        <button
          className="btn-reject"
          onClick={(e) => {
            e.stopPropagation();
            onReject?.(account);
          }}
        >
          거부
        </button>
      </div>
    </ApprovalRowItem>
  );
}

export default ApprovalItem;
