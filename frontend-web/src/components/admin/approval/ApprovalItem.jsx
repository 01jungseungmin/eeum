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
    .btn-hold {
      background: white;
      color: #595959;
      border-color: #d9d9d9;
      &:hover {
        background: #f5f5f5;
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
  const navigate = useNavigate(); // 🔥 내비게이트 함수 선언

  const { ownerInfo, nickname, name, email } = account;
  const ownerInfoId = ownerInfo?.ownerInfoId; // 🔥 라우팅에 사용할 고유 ID 추출

  const statusMap = {
    PENDING: '대기중',
    REJECTED: '반려',
    APPROVED: '승인완료',
  };

  // 날짜 포맷터 함수
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return `${date.getFullYear()}년 ${String(date.getMonth() + 1).padStart(2, '0')}월 ${String(date.getDate()).padStart(2, '0')}일`;
  };

  const handleItemClick = () => {
    if (!ownerInfoId) {
      alert('유효한 신청 ID를 찾을 수 없습니다.');
      return;
    }

    navigate(`/admin/approval/${ownerInfoId}`);
  };

  return (
    <ApprovalRowItem $isSelected={isSelected} onClick={handleItemClick}>
      <div className="store-info-zone">
        <div className="title-row">
          <h3>{nickname || '상점명 없음'}</h3>
          <StatusBadge $status={ownerInfo?.approvalStatus}>
            {statusMap[ownerInfo?.approvalStatus] || '알 수 없음'}
          </StatusBadge>
        </div>
        <div className="details-row">
          <span>
            <strong>대표자명:</strong>
            {name}
          </span>
          <span>
            <strong>이메일:</strong>
            {email}
          </span>
          <span>
            <strong>연락처:</strong>
            {ownerInfo?.phone || '-'}
          </span>
          <span>
            <strong>신청일:</strong>
            {formatDate(ownerInfo?.createdAt)}
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
