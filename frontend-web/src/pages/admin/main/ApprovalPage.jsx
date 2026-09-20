import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { Clock, CheckCircle2, XCircle, BarChart3 } from 'lucide-react';
import ApprovalListContainer from '../../../components/admin/approval/ApprovalListContainer';
import { approvalApi } from '../../../api/admin/approvalApi';
import { clickableCardStyle } from '../../../components/common/cardFilterStyle';

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
  ${clickableCardStyle}
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

const LIST_SIZE = 100;

const STATUS_LABEL = {
  PENDING: '승인 대기',
  APPROVED: '승인 완료',
  REJECTED: '반려',
};

const EMPTY_MESSAGE = {
  PENDING: '가입 승인 대기 내역이 존재하지 않습니다.',
  APPROVED: '승인 완료된 내역이 존재하지 않습니다.',
  REJECTED: '반려된 내역이 존재하지 않습니다.',
};

const ListNote = styled.p`
  margin: 0;
  font-size: 12px;
  color: #8c8c8c;
  text-align: right;
`;

function ApprovalPage() {
  const [approvalData, setApprovalData] = useState([]);
  // 상단 카드로 고르는 신청 상태 (서버는 상태별로만 목록을 내려준다)
  const [selectedStatus, setSelectedStatus] = useState('PENDING');
  const [counts, setCounts] = useState(null);
  const [listTotal, setListTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  // 신청 목록 불러오기
  const fetchApplications = useCallback(async (status) => {
    setLoading(true);
    try {
      const response = await approvalApi.getApplications({
        approvalStatus: status,
        size: LIST_SIZE,
      });

      const page = response.data?.data;
      if (page?.content) {
        setApprovalData(page.content);
        setListTotal(page.totalElements ?? page.content.length);
      }
    } catch (error) {
      console.error('데이터 로드 실패:', error);
      setApprovalData([]);
      const statusCode = error.response?.status;
      if (statusCode === 401 || statusCode === 403) {
        alert(
          '목록을 불러올 권한이 없습니다. 관리자 계정으로 다시 로그인해 주세요.',
        );
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // 카드에 보여줄 상태별 전체 건수 — 건수만 필요해서 1건씩 요청하고 totalElements 를 쓴다
  const fetchCounts = useCallback(async () => {
    const statuses = Object.keys(STATUS_LABEL);
    const results = await Promise.allSettled(
      statuses.map((status) =>
        approvalApi.getApplications({ approvalStatus: status, size: 1 }),
      ),
    );

    setCounts(
      Object.fromEntries(
        statuses.map((status, index) => [
          status,
          results[index].status === 'fulfilled'
            ? (results[index].value.data?.data?.totalElements ?? 0)
            : 0,
        ]),
      ),
    );
  }, []);

  useEffect(() => {
    queueMicrotask(() => fetchApplications(selectedStatus));
  }, [selectedStatus, fetchApplications]);

  useEffect(() => {
    queueMicrotask(() => fetchCounts());
  }, [fetchCounts]);

  // 승인/거부 후에는 목록과 카드 건수를 함께 갱신한다
  const refresh = () => {
    fetchApplications(selectedStatus);
    fetchCounts();
  };

  // 회원 승인
  const handleApprove = async (account) => {
    const targetId =
      account.ownerInfoId || account.accountId || account.ownerInfo?.ownerInfoId;
    if (!targetId) {
      alert('유효한 신청 ID를 찾을 수 없습니다.');
      return;
    }

    const displayName = account.ownerName || account.name || '';
    const confirmApprove = window.confirm(
      `${displayName} 사장님의 가입을 승인하시겠습니까?`,
    );
    if (!confirmApprove) return;

    try {
      const response = await approvalApi.approveOwner(targetId);

      if (response.data?.success) {
        alert(`${displayName} 사장님의 가입이 승인되었습니다.`);
        refresh();
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
    const targetId =
      account.ownerInfoId || account.accountId || account.ownerInfo?.ownerInfoId;
    if (!targetId) {
      alert('유효한 신청 ID를 찾을 수 없습니다.');
      return;
    }

    const displayName = account.ownerName || account.name || '';
    const userInputReason = window.prompt(
      `${displayName} 사장님의 가입을 거부하는 사유를 입력해주세요:`,
    );

    if (userInputReason === null) return; // 취소 클릭
    if (userInputReason.trim() === '') {
      alert('거부 사유를 반드시 입력해야 합니다.');
      return;
    }

    try {
      const response = await approvalApi.rejectOwner(targetId, userInputReason);

      if (response.data?.success) {
        alert(`${displayName} 사장님의 가입 신청이 거절되었습니다.`);
        refresh();
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

  const countText = (status) => (counts ? counts[status] : '-');
  const decided = counts ? counts.APPROVED + counts.REJECTED : 0;
  // 심사가 끝난(승인+반려) 건 중 반려 비율
  const rejectRate =
    counts && decided > 0 ? ((counts.REJECTED / decided) * 100).toFixed(1) : null;

  return (
    <PageWrapper>
      <PageHeader>
        <div className="title-side">
          <h1>사장 가입 승인</h1>
          <p>
            {counts
              ? `총 신청 ${counts.PENDING + decided}건 · 승인 대기 ${counts.PENDING}건 · 승인 ${counts.APPROVED}건 · 반려 ${counts.REJECTED}건`
              : '신청 현황을 불러오는 중입니다...'}
          </p>
        </div>
      </PageHeader>

      <SummaryGrid>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#faad14"
          $borderColor="#ffe58f"
          $subColor="#faad14"
          $clickable
          $active={selectedStatus === 'PENDING'}
          onClick={() => setSelectedStatus('PENDING')}
        >
          <div className="info">
            <span>승인 대기</span>
            <h2>{countText('PENDING')}</h2>
            <p>심사 요청된 신청</p>
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
          $clickable
          $active={selectedStatus === 'APPROVED'}
          onClick={() => setSelectedStatus('APPROVED')}
        >
          <div className="info">
            <span>승인 완료</span>
            <h2>{countText('APPROVED')}</h2>
            <p>누적</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fff1f0"
          $iconColor="#cf1322"
          $borderColor="#ffccc7"
          $subColor="#cf1322"
          $clickable
          $active={selectedStatus === 'REJECTED'}
          onClick={() => setSelectedStatus('REJECTED')}
        >
          <div className="info">
            <span>반려</span>
            <h2>{countText('REJECTED')}</h2>
            <p>누적</p>
          </div>
          <div className="icon-wrapper">
            <XCircle size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#f0f5ff"
          $iconColor="#2f54eb"
          $borderColor="#d6e4ff"
          $subColor="#2f54eb"
        >
          <div className="info">
            <span>반려율</span>
            <h2>{rejectRate === null ? '-' : `${rejectRate}%`}</h2>
            <p>심사 완료 건 기준</p>
          </div>
          <div className="icon-wrapper">
            <BarChart3 size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <ApprovalListContainer
        listData={approvalData}
        loading={loading}
        emptyMessage={EMPTY_MESSAGE[selectedStatus]}
        onApprove={handleApprove}
        onReject={handleReject}
      />

      {listTotal > LIST_SIZE && (
        <ListNote>
          {STATUS_LABEL[selectedStatus]} {listTotal}건 중 최근 {LIST_SIZE}건만
          표시하고 있어요.
        </ListNote>
      )}
    </PageWrapper>
  );
}

export default ApprovalPage;
