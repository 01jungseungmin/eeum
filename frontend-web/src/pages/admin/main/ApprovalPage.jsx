import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Clock, CheckCircle2, XCircle, BarChart3 } from 'lucide-react';
import ApprovalListContainer from '../../../components/admin/ApprovalListContainer';
import axios from 'axios';

const PageWrapper = styled.div`
  padding: 30px;
  background-color: #fcfcfc;
  display: flex;
  flex-direction: column;
  gap: 24px;
  font-family: 'Pretendard', sans-serif;
`;

const PageHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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

const HistoryButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: white;
  color: #262626;
  border: 1px solid #d9d9d9;
  padding: 8px 14px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  &:hover {
    background-color: #f5f5f5;
  }
`;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
`;

const SummaryCard = styled.div`
  background: white;
  border: 1px solid ${(props) => props.$borderColor || '#f0f0f0'};
  border-radius: 16px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  .info {
    span {
      font-size: 12px;
      color: #8c8c8c;
      font-weight: 500;
    }
    h2 {
      margin: 8px 0 4px 0;
      font-size: 26px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 12px;
      color: ${(props) => props.$subColor || '#bfbfbf'};
      font-weight: 600;
    }
  }
  .icon-wrapper {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: ${(props) => props.$iconBg};
    color: ${(props) => props.$iconColor};
  }
`;

function AdminApprovalPage() {
  const [approvalData, setApprovalData] = useState([]);

  const fetchApplications = () => {
    const token =
      localStorage.getItem('accessToken') ||
      sessionStorage.getItem('accessToken');

    axios
      .get('http://localhost:8080/admin/accounts/owners/applications', {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
      })
      .then((response) => {
        // 백엔드 응답 구조(success 여부 등)에 맞춰 안전하게 체크
        if (response.data?.success && response.data?.data?.content) {
          setApprovalData(response.data.data.content);
        } else if (response.data?.data?.content) {
          // 혹시 success 필드가 없는 구조일 경우를 대비한 예비 로직
          setApprovalData(response.data.data.content);
        }
      })
      .catch((error) => {
        console.error('데이터 로드 실패:', error);
        const statusCode = error.response?.status;
        if (statusCode === 401 || statusCode === 403) {
          alert(
            '목록을 불러올 권한이 없습니다. 관리자 계정으로 다시 로그인해 주세요.',
          );
        }
      });
  };

  useEffect(() => {
    fetchApplications();
  }, []);

  // 회원 승인
  const handleApprove = async (account) => {
    const targetId = account.ownerInfo?.ownerInfoId;
    if (!targetId) {
      alert('유효한 신청 ID를 찾을 수 없습니다.');
      return;
    }

    const confirmApprove = window.confirm(
      `${account.name} 사장님의 가입을 승인하시겠습니까?`,
    );
    if (!confirmApprove) return;

    try {
      // 저장소에서 토큰 꺼내오기 (관리자 권환인지 확인)
      const token =
        localStorage.getItem('accessToken') ||
        sessionStorage.getItem('accessToken');

      const response = await axios.patch(
        `http://localhost:8080/admin/accounts/owners/${targetId}/approve`,
        {}, // PATCH나 POST 요청 시 보낼 바디가 없다면 빈 객체{}로 명시적으로 전달
        {
          headers: {
            Authorization: token ? `Bearer ${token}` : '', // 토큰이 존재할 때만 Bearer 형태로 주입
          },
        },
      );

      if (response.data.success) {
        alert(`${account.name} 사장님의 가입이 승인되었습니다.`);
        fetchApplications();
      }
    } catch (error) {
      console.error('승인 처리 중 오류 발생:', error);
      const statusCode = error.response?.status;
      const errorMessage =
        error.response?.data?.message || '승인 처리에 실패했습니다.';

      if (statusCode === 401) {
        alert('인증에 실패했습니다. 관리자 계정으로 다시 로그인해 주세요.');
      } else {
        alert(`[에러 ${statusCode}] ${errorMessage}`);
      }
    }
  };

  // 회원 거절
  const handleReject = async (account) => {
    const targetId = account.ownerInfo?.ownerInfoId;
    if (!targetId) {
      alert('유효한 신청 ID를 찾을 수 없습니다.');
      return;
    }

    const userInputReason = window.prompt(
      `${account.name} 사장님의 가입을 거부하는 사유를 입력해주세요:`,
    );

    // 취소 버튼을 누른 경우
    if (userInputReason === null) return;

    if (userInputReason.trim() === '') {
      alert('거부 사유를 반드시 입력해야 합니다.');
      return;
    }

    try {
      // 저장소에서 토큰 꺼내오기 (관리자 권환인지 확인)
      const token =
        localStorage.getItem('accessToken') ||
        sessionStorage.getItem('accessToken');

      // 관리자 권환으로만 접근 가능한 API 엔드포인트에 PATCH 요청 보내기
      const response = await axios.patch(
        `http://localhost:8080/admin/accounts/owners/${targetId}/reject`,
        { reason: userInputReason },
        {
          headers: {
            Authorization: token ? `Bearer ${token}` : '',
          },
        },
      );

      if (response.data.success) {
        alert(`${account.name} 사장님의 가입 신청이 거절되었습니다.`);
        fetchApplications(); // 목록 새로고침
      }
    } catch (error) {
      console.error('거절 처리 중 오류 발생:', error);
      const statusCode = error.response?.status;
      const errorMessage =
        error.response?.data?.message || '거절 처리에 실패했습니다.';

      if (statusCode === 401) {
        alert('인증에 실패했습니다. 관리자 계정으로 다시 로그인해 주세요.');
      } else {
        alert(`[에러 ${statusCode}] ${errorMessage}`);
      }
    }
  };

  return (
    <PageWrapper>
      <PageHeader>
        <div className="title-side">
          <h1>사장 가입 승인 대기</h1>
          <p>총 신청 건수: 평균 4건 · 승인 432건 · 정보 확인 필요 5건</p>
        </div>
        <HistoryButton onClick={() => alert('승인 이력 조회 페이지 이동')}>
          📋 승인 이력 조회
        </HistoryButton>
      </PageHeader>

      <SummaryGrid>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#faad14"
          $borderColor="#ffe58f"
          $subColor="#faad14"
        >
          <div className="info">
            <span>대기 대기</span>
            <h2>12</h2>
            <p>평균 대기 4시간</p>
          </div>
          <div className="icon-wrapper">
            <Clock size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
          $borderColor="#b7eb8f"
          $subColor="#2d5a43"
        >
          <div className="info">
            <span>승인 완료</span>
            <h2>38</h2>
            <p>이번 주</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fff5f5"
          $iconColor="#ff4d4f"
          $borderColor="#ffccc7"
          $subColor="#ff4d4f"
        >
          <div className="info">
            <span>거부율</span>
            <h2>7.2%</h2>
            <p>이번 달</p>
          </div>
          <div className="icon-wrapper">
            <XCircle size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#f0f5ff"
          $iconColor="#2f54eb"
          $borderColor="#adc6ff"
          $subColor="#2f54eb"
        >
          <div className="info">
            <span>평균 처리시간</span>
            <h2>4시간</h2>
            <p>지난 30일</p>
          </div>
          <div className="icon-wrapper">
            <BarChart3 size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <ApprovalListContainer
        listData={approvalData}
        onApprove={handleApprove}
        onReject={handleReject}
      />
    </PageWrapper>
  );
}

export default AdminApprovalPage;
