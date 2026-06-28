import React, { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { approvalApi } from '../../api/owner/ApprovalApi';

function ApprovalGuard() {
  const [approvalStatus, setApprovalStatus] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkStatus = async () => {
      try {
        const response = await approvalApi.getOwnerStoreChecklist();
        if (response.data.success) {
          // 백엔드 명세에 맞춘 승인 상태 매핑 ('PENDING', 'APPROVED', 'REJECTED')
          setApprovalStatus(response.data.data.approvalStatus);
        }
      } catch (error) {
        console.error('가드 승인 상태 확인 실패:', error);
        setApprovalStatus('REJECTED'); // 실패 시 안전하게 진입 차단
      } finally {
        setLoading(false);
      }
    };

    checkStatus();
  }, []);

  if (loading) {
    return (
      <div
        style={{
          padding: '40px',
          textAlign: 'center',
          fontSize: '14px',
          color: '#666',
        }}
      >
        권한을 확인하는 중입니다...
      </div>
    );
  }

  // 최종 승인이 아니라면 무조건 승인 상태 페이지로 강제 리다이렉트
  if (approvalStatus !== 'APPROVED') {
    return <Navigate to="/approval-status" replace />;
  }

  // 승인 완료 유저만 원래 가려던 페이지 허용
  return <Outlet />;
}

export default ApprovalGuard;
