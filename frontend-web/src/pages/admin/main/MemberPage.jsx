import React, { useEffect, useState, useMemo } from 'react';
import styled from 'styled-components';
import MemberOverview from '../../../components/admin/member/MemberOverview';
import MemberFilterBar from '../../../components/admin/member/MemberFilterBar';
import MemberTabs from '../../../components/admin/member/MemberTabs';
import MemberTable from '../../../components/admin/member/MemberTable';
import { memberApi } from '../../../api/admin/memberApi';

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

function MemberPage() {
  const [activeTab, setActiveTab] = useState('all');
  const [currentStatusFilter, setCurrentStatusFilter] = useState('ALL');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);
  const [originMemberList, setOriginMemberList] = useState([]);

  const [currentPage, setCurrentPage] = useState(0);
  const PAGE_SIZE = 10;

  const getMemberName = (member) => {
    return member?.name || member?.ownerName || '이름 없음';
  };

  const fetchAllMembers = async () => {
    try {
      const response = await memberApi.getAllMembers(0, 1000);
      if (response.data && response.data.success) {
        setOriginMemberList(response.data.data.content || []);
      }
    } catch (error) {
      console.error('멤버 데이터 로드 실패:', error);
    }
  };

  useEffect(() => {
    fetchAllMembers();
  }, []);

  const tabCounts = useMemo(() => {
    const activeUsers = originMemberList.filter(
      (m) => m.status !== 'WITHDRAWN',
    );

    return {
      all: activeUsers.length,
      general: activeUsers.filter((m) => m.role === 'ROLE_USER').length,
      owner: activeUsers.filter((m) => m.role === 'ROLE_OWNER').length,
      suspended: activeUsers.filter((m) => m.status === 'SUSPENDED').length,
      withdrawn: originMemberList.filter((m) => m.status === 'WITHDRAWN')
        .length,
    };
  }, [originMemberList]);

  const filteredList = useMemo(() => {
    let result = [...originMemberList];

    if (activeTab !== 'withdrawn') {
      result = result.filter((m) => m.status !== 'WITHDRAWN');

      if (activeTab === 'general') {
        result = result.filter((m) => m.role === 'ROLE_USER');
      } else if (activeTab === 'owner') {
        result = result.filter((m) => m.role === 'ROLE_OWNER');
      } else if (activeTab === 'suspended') {
        result = result.filter((m) => m.status === 'SUSPENDED');
      }

      if (currentStatusFilter !== 'ALL') {
        result = result.filter((m) => m.status === currentStatusFilter);
      }
    } else {
      result = result.filter((m) => m.status === 'WITHDRAWN');
    }

    if (searchKeyword.trim()) {
      const keyword = searchKeyword.toLowerCase().trim();
      result = result.filter((m) => {
        const name = getMemberName(m).toLowerCase();
        const email = (m.email || '').toLowerCase();
        const storeName = (m.storeName || m.nickname || '').toLowerCase();

        return (
          name.includes(keyword) ||
          email.includes(keyword) ||
          storeName.includes(keyword)
        );
      });
    }

    return result;
  }, [activeTab, currentStatusFilter, searchKeyword, originMemberList]);

  const totalPages = useMemo(() => {
    const pages = Math.ceil(filteredList.length / PAGE_SIZE);
    return pages === 0 ? 1 : pages;
  }, [filteredList]);

  const pagedMemberList = useMemo(() => {
    const start = currentPage * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    return filteredList.slice(start, end);
  }, [currentPage, filteredList]);

  useEffect(() => {
    setCurrentPage(0);
    setSelectedIds([]);
  }, [activeTab, currentStatusFilter, searchKeyword]);

  const handleSelectRow = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  const handleSelectAll = () => {
    const visibleIds = pagedMemberList.map((m) => m.accountId);
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
      fetchAllMembers();
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
      fetchAllMembers();
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
      fetchAllMembers();
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
      fetchAllMembers();
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
        fetchAllMembers();
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
      />

      <MemberFilterBar
        selectedCount={selectedIds.length}
        onBulkSuspend={handleBulkSuspend}
        onBulkActivate={handleBulkActivate}
        onApplyFilter={(status) => setCurrentStatusFilter(status)}
        onSearch={(keyword) => setSearchKeyword(keyword)}
      />

      <MemberTabs
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        tabCounts={tabCounts}
      />

      <MemberTable
        data={pagedMemberList}
        selectedIds={selectedIds}
        onSelectRow={handleSelectRow}
        onSelectAll={handleSelectAll}
        onActionSuspend={handleToggleSuspend}
        onActionWithdraw={handleActionWithdraw}
        onActionRestore={handleRestoreMember}
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
    </div>
  );
}

export default MemberPage;
