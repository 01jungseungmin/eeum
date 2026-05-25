import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import styled from 'styled-components';
import { ArrowLeft, FileText, Image as ImageIcon, Check } from 'lucide-react';
import ApprovalProfileCard from '../../../components/admin/ApprovalProfileCard';
import ApprovalDetailPanel from '../../../components/admin/ApprovalDetailPanel';

const DetailContainer = styled.div`
  padding: 30px; /* AdminApprovalPage와 동일한 패딩값 매칭 */
  display: flex;
  flex-direction: column;
  gap: 24px;
  font-family: 'Pretendard', sans-serif;
`;

const DetailHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;

  .btn-back {
    background: white;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #595959;
    transition: background 0.2s;
    &:hover {
      background: #f5f5f5;
    }
  }

  .title-side {
    h1 {
      margin: 0 0 6px 0;
      font-size: 24px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 13px;
      color: #8c8c8c;
    }
  }
`;

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
`;

function ApprovalDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [account, setAccount] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [adminMemo, setAdminMemo] = useState('');

  useEffect(() => {
    const fetchDetailData = async () => {
      try {
        setIsLoading(true);
        const token =
          localStorage.getItem('accessToken') ||
          sessionStorage.getItem('accessToken');
        const response = await axios.get(
          `http://localhost:8080/admin/accounts/owners/${id}`,
          {
            headers: { Authorization: token ? `Bearer ${token}` : '' },
          },
        );

        if (response.data?.success && response.data?.data) {
          setAccount(response.data.data);
        } else {
          alert('상세 정보를 불러올 수 없습니다.');
          navigate('/admin/approval');
        }
      } catch (error) {
        console.error(error);
        alert('데이터 로드 실패로 목록으로 이동합니다.');
        navigate('/admin/approval');
      } finally {
        setIsLoading(false);
      }
    };

    if (id) fetchDetailData();
  }, [id, navigate]);

  if (isLoading)
    return (
      <DetailContainer style={{ textAlign: 'center', padding: '100px' }}>
        데이터 조회 중...
      </DetailContainer>
    );
  if (!account) return null;

  const { ownerInfo, nickname, name, email } = account;

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
  };

  const handleApprove = async () => {
    if (!window.confirm(`${name} 사장님의 가입 신청을 승인하시겠습니까?`))
      return;
    try {
      const token =
        localStorage.getItem('accessToken') ||
        sessionStorage.getItem('accessToken');
      const response = await axios.patch(
        `http://localhost:8080/admin/accounts/owners/${id}/approve`,
        {},
        { headers: { Authorization: token ? `Bearer ${token}` : '' } },
      );
      if (response.data?.success) {
        alert('성공적으로 승인되었습니다.');
        navigate('/admin/approval');
      }
    } catch (error) {
      alert(error.response?.data?.message || '승인 처리 중 오류 발생');
    }
  };

  const handleHold = () => {
    alert('보류 처리 예정');
  };

  const handleReject = async () => {
    const reason = window.prompt(
      '거부 사유를 입력해주세요:',
      '서류 미비 및 사업자 정보 불일치',
    );
    if (reason === null) return;
    if (!reason.trim()) return alert('거부 사유 입력은 필수입니다.');

    try {
      const token =
        localStorage.getItem('accessToken') ||
        sessionStorage.getItem('accessToken');
      const response = await axios.patch(
        `http://localhost:8080/admin/accounts/owners/${id}/reject`,
        { reason: reason },
        { headers: { Authorization: token ? `Bearer ${token}` : '' } },
      );
      if (response.data?.success) {
        alert('가입 신청이 거절 처리되었습니다.');
        navigate('/admin/approval');
      }
    } catch (error) {
      alert(error.response?.data?.message || '거절 처리 중 오류 발생');
    }
  };

  return (
    <DetailContainer>
      <DetailHeader>
        <button
          className="btn-back"
          onClick={() => navigate('/admin/approval')}
        >
          <ArrowLeft size={18} />
        </button>
        <div className="title-side">
          <h1>사장 승인</h1>
          <p>사장 가입 신청 상세 정보</p>
        </div>
      </DetailHeader>

      <MainGrid>
        <ApprovalProfileCard account={account} />

        {/* 우측 패널 컴포넌트에 버튼 클릭 콜백들 그대로 주입 */}
        <ApprovalDetailPanel
          account={account}
          memo={adminMemo}
          onMemoChange={setAdminMemo}
          onGoBack={() => navigate('/admin/approval')}
          onHold={handleHold}
          onReject={handleReject}
          onApprove={handleApprove}
        />
      </MainGrid>
    </DetailContainer>
  );
}

export default ApprovalDetailPage;
