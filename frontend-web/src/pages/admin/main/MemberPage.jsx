import { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import MemberOverview from '../../../components/admin/member/MemberOverview';
import MemberFilterBar from '../../../components/admin/member/MemberFilterBar';
import MemberTabs from '../../../components/admin/member/MemberTabs';
import MemberTable from '../../../components/admin/member/MemberTable';
import MemberDetailModal from '../../../components/admin/member/MemberDetailModal';
import SanctionHistoryModal from '../../../components/admin/sanction/SanctionHistoryModal';
import { memberApi } from '../../../api/admin/memberApi';
import { MEMBER_TAB_QUERY } from '../../../constants/memberConstants';

const PaginationContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
`;

const PageButton = styled.button`
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  border: 1px solid ${(props) => (props.$active ? '#2d5a43' : '#d9d9d9')};
  background: ${(props) => (props.$active ? '#2d5a43' : 'white')};
  color: ${(props) => (props.$active ? 'white' : '#555')};
  font-weight: ${(props) => (props.$active ? '700' : '500')};
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: #2d5a43;
    color: ${(props) => (props.$active ? 'white' : '#2d5a43')};
  }

  &:disabled {
    background: #f5f5f5;
    color: #ccc;
    border-color: #d9d9d9;
    cursor: not-allowed;
  }
`;

const PAGE_SIZE = 10;
const SEARCH_DEBOUNCE_MS = 300;

function MemberPage() {
  const [activeTab, setActiveTab] = useState('all');
  const [currentStatusFilter, setCurrentStatusFilter] = useState('ALL');
  // 입력 중인 검색어와, 잠시 멈춘 뒤 서버에 실제로 보내는 검색어를 나눈다
  const [keywordInput, setKeywordInput] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [tabCounts, setTabCounts] = useState({
    all: 0,
    general: 0,
    owner: 0,
    suspended: 0,
    withdrawn: 0,
  });
  const [sanctionTarget, setSanctionTarget] = useState(null); // { accountId, name } | null
  const [detailTargetId, setDetailTargetId] = useState(null);

  // 늦게 도착한 이전 조회 응답이 최신 화면을 덮어쓰지 않게 요청 순번을 기록한다
  const requestSeq = useRef(0);

  // 탭 + 상태 + 검색어에 맞는 회원 목록 조회
  const fetchMembers = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);

    try {
      const keyword = searchKeyword.trim();
      let response;

      // 탈퇴 회원은 전용 API 로 조회한다 (전용 API 는 검색을 지원하지 않아 검색어가 있으면 목록 API 를 쓴다)
      if (activeTab === 'withdrawn' && !keyword) {
        response = await memberApi.getWithdrawnMembers({
          page: currentPage,
          size: PAGE_SIZE,
        });
      } else {
        const params = { page: currentPage, size: PAGE_SIZE };
        if (activeTab === 'withdrawn') {
          params.status = 'WITHDRAWN';
        } else {
          Object.assign(params, MEMBER_TAB_QUERY[activeTab]);
          if (activeTab !== 'suspended' && currentStatusFilter !== 'ALL') {
            params.status = currentStatusFilter;
          }
        }
        if (keyword) params.keyword = keyword;
        response = await memberApi.getMembers(params);
      }

      if (seq !== requestSeq.current) return;
      if (response.data?.success) {
        const page = response.data.data;
        setMembers(page.content || []);
        setTotalPages(Math.max(1, page.totalPages || 1));
      }
    } catch (error) {
      console.error('멤버 데이터 로드 실패:', error);
      if (seq === requestSeq.current) setMembers([]);
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, [activeTab, currentStatusFilter, searchKeyword, currentPage]);

  // 탭에 보여줄 전체 건수 — 건수만 필요해서 1건씩 요청하고 totalElements 를 쓴다
  const fetchCounts = useCallback(async () => {
    const requests = {
      all: memberApi.getMembers({ size: 1 }),
      general: memberApi.getMembers({ size: 1, ...MEMBER_TAB_QUERY.general }),
      owner: memberApi.getMembers({ size: 1, ...MEMBER_TAB_QUERY.owner }),
      suspended: memberApi.getMembers({
        size: 1,
        ...MEMBER_TAB_QUERY.suspended,
      }),
      withdrawn: memberApi.getWithdrawnMembers({ size: 1 }),
    };
    const keys = Object.keys(requests);
    const results = await Promise.allSettled(Object.values(requests));

    setTabCounts(
      Object.fromEntries(
        keys.map((key, index) => [
          key,
          results[index].status === 'fulfilled'
            ? (results[index].value.data?.data?.totalElements ?? 0)
            : 0,
        ]),
      ),
    );
  }, []);

  useEffect(() => {
    queueMicrotask(() => fetchMembers());
  }, [fetchMembers]);

  useEffect(() => {
    queueMicrotask(() => fetchCounts());
  }, [fetchCounts]);

  // 검색어는 입력이 잠시 멈췄을 때만 서버로 보낸다
  useEffect(() => {
    const timerId = setTimeout(() => {
      setSearchKeyword(keywordInput);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timerId);
  }, [keywordInput]);

  // 목록 조건이 바뀌면 첫 페이지로 돌아가고 선택을 초기화한다
  const resetView = () => {
    setCurrentPage(0);
    setSelectedIds([]);
  };

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    resetView();
  };

  // 필터바는 { status, keyword } 를 함께 넘긴다
  const handleApplyFilter = ({ status, keyword }) => {
    setCurrentStatusFilter(status);
    setKeywordInput(keyword);
    resetView();
  };

  // 처리 후에는 목록과 탭 건수를 함께 갱신한다
  const refresh = () => {
    fetchMembers();
    fetchCounts();
  };

  const handleSelectRow = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  const handleSelectAll = () => {
    const visibleIds = members.map((m) => m.accountId);
    const isAllVisibleSelected =
      visibleIds.length > 0 &&
      visibleIds.every((id) => selectedIds.includes(id));

    if (isAllVisibleSelected) {
      setSelectedIds((prev) => prev.filter((id) => !visibleIds.includes(id)));
    } else {
      setSelectedIds((prev) => Array.from(new Set([...prev, ...visibleIds])));
    }
  };

  const handleToggleSuspend = async (targetId, targetName, currentStatus) => {
    if (!targetId) return;

    const isRelease = currentStatus === 'SUSPENDED';
    const confirmMessage = isRelease
      ? `[${targetName}] 회원의 정지를 해제하시겠습니까?`
      : `[${targetName}] 회원을 정지하시겠습니까?`;

    if (!window.confirm(confirmMessage)) return;

    try {
      if (isRelease) {
        await memberApi.activateAccount(targetId);
        alert(`${targetName} 회원의 정지가 해제되었습니다.`);
      } else {
        await memberApi.suspendAccount(targetId);
        alert(`${targetName} 회원이 정지 처리되었습니다.`);
      }
      setSelectedIds((prev) => prev.filter((id) => id !== targetId));
      refresh();
    } catch (error) {
      console.error('정지/해제 처리 중 에러:', error);
      alert('요청 처리 중 오류가 발생했습니다.');
    }
  };

  const handleBulkSuspend = async () => {
    if (selectedIds.length === 0) return alert('대상을 선택해 주세요.');

    if (
      !window.confirm(
        `선택한 ${selectedIds.length}명의 회원을 정지하시겠습니까?`,
      )
    )
      return;

    try {
      await memberApi.bulkSuspend(selectedIds);
      alert('선택한 회원의 정지 처리가 완료되었습니다.');
      setSelectedIds([]);
      refresh();
    } catch (error) {
      console.error('일괄 정지 처리 실패:', error);
      alert('일괄 정지 처리 중 오류가 발생했습니다.');
    }
  };

  const handleBulkActivate = async () => {
    if (selectedIds.length === 0) return alert('대상을 선택해 주세요.');

    if (
      !window.confirm(
        `선택한 ${selectedIds.length}명의 정지를 해제하시겠습니까?`,
      )
    )
      return;

    try {
      await memberApi.bulkActivate(selectedIds);
      alert('선택한 회원의 정지 해제가 완료되었습니다.');
      setSelectedIds([]);
      refresh();
    } catch (error) {
      console.error('일괄 해제 처리 실패:', error);
      alert('일괄 해제 처리 중 오류가 발생했습니다.');
    }
  };

  const handleActionWithdraw = async (accountId, name) => {
    if (!accountId) return;

    if (!window.confirm(`[${name}] 회원을 정말로 강제 탈퇴시키겠습니까?`))
      return;

    try {
      await memberApi.withdrawAccount(accountId);
      alert(`${name} 회원이 탈퇴 처리되었습니다.`);
      setSelectedIds((prev) => prev.filter((id) => id !== accountId));
      refresh();
    } catch (error) {
      console.error('강제 탈퇴 에러:', error);
      alert('탈퇴 처리 중 오류가 발생했습니다.');
    }
  };

  const handleRestoreMember = async (accountId, name) => {
    if (!accountId) return;

    if (
      !window.confirm(
        `[${name}] 회원의 탈퇴를 취소하고 계정을 복구하시겠습니까?`,
      )
    )
      return;

    try {
      const response = await memberApi.cancelWithdrawal(accountId);
      if (response.data && response.data.success) {
        alert(`${name} 회원의 탈퇴 해제(복구)가 완료되었습니다.`);
        refresh();
      }
    } catch (error) {
      console.error('탈퇴 복구 실패:', error);
      alert(
        error.response?.data?.error?.message ||
          '탈퇴 해제 처리 중 오류가 발생했습니다.',
      );
    }
  };

  return (
    <div style={{ padding: '10px' }}>
      <MemberOverview
        total={tabCounts.all}
        suspended={tabCounts.suspended}
        withdrawn={tabCounts.withdrawn}
      />

      <MemberFilterBar
        selectedCount={selectedIds.length}
        onBulkSuspend={handleBulkSuspend}
        onBulkActivate={handleBulkActivate}
        onApplyFilter={handleApplyFilter}
        statusDisabled={activeTab === 'suspended' || activeTab === 'withdrawn'}
      />

      <MemberTabs
        activeTab={activeTab}
        setActiveTab={handleTabChange}
        tabCounts={tabCounts}
      />

      <MemberTable
        data={members}
        loading={loading}
        selectedIds={selectedIds}
        onSelectRow={handleSelectRow}
        onSelectAll={handleSelectAll}
        onActionSuspend={handleToggleSuspend}
        onActionWithdraw={handleActionWithdraw}
        onActionRestore={handleRestoreMember}
        onShowSanctionHistory={(accountId, name) =>
          setSanctionTarget({ accountId, name })
        }
        onShowDetail={setDetailTargetId}
      />

      {totalPages > 1 && (
        <PaginationContainer>
          <PageButton
            disabled={currentPage === 0}
            onClick={() => setCurrentPage((prev) => prev - 1)}
          >
            &lt;
          </PageButton>
          {Array.from({ length: totalPages }, (_, index) => (
            <PageButton
              key={index}
              $active={currentPage === index}
              onClick={() => setCurrentPage(index)}
            >
              {index + 1}
            </PageButton>
          ))}
          <PageButton
            disabled={currentPage === totalPages - 1}
            onClick={() => setCurrentPage((prev) => prev + 1)}
          >
            &gt;
          </PageButton>
        </PaginationContainer>
      )}

      {detailTargetId && (
        <MemberDetailModal
          accountId={detailTargetId}
          onClose={() => setDetailTargetId(null)}
        />
      )}

      {sanctionTarget && (
        <SanctionHistoryModal
          targetType="ACCOUNT"
          targetId={sanctionTarget.accountId}
          targetLabel={`${sanctionTarget.name} (회원 #${sanctionTarget.accountId})`}
          onClose={() => setSanctionTarget(null)}
        />
      )}
    </div>
  );
}

export default MemberPage;
