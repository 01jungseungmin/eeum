import React, { useEffect, useState, useMemo } from 'react';
import MemberOverview from '../../../components/admin/member/MemberOverview';
import MemberFilterBar from '../../../components/admin/member/MemberFilterBar';
import MemberTabs from '../../../components/admin/member/MemberTabs';
import MemberTable from '../../../components/admin/member/MemberTable';
import axios from 'axios';
import styled from 'styled-components';

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
  const [selectedIds, setSelectedIds] = useState([]);
  const [originMemberList, setOriginMemberList] = useState([]);

  const [currentPage, setCurrentPage] = useState(0);
  const PAGE_SIZE = 10;

  const token =
    localStorage.getItem('accessToken') ||
    sessionStorage.getItem('accessToken');
  const headers = { Authorization: token ? `Bearer ${token}` : '' };

  const fetchAllMembers = () => {
    axios
      .get('http://localhost:8080/admin/accounts?page=0&size=1000', {
        headers,
      })
      .then((response) => {
        if (response.data && response.data.success) {
          const apiData = response.data.data;
          setOriginMemberList(apiData.content || []);
        }
      })
      .catch((error) => {
        console.error('멤버 데이터 로드 실패:', error);
      });
  };

  useEffect(() => {
    fetchAllMembers();
  }, []);

  const tabCounts = useMemo(() => {
    // 탈퇴하지 않은 정상 유저들 베이스
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

    // '탈퇴 회원' 탭이 아닐 때는 목록에서 탈퇴자들을 기본적으로 숨김
    if (activeTab !== 'withdrawn') {
      result = result.filter((m) => m.status !== 'WITHDRAWN');

      if (activeTab === 'general') {
        result = result.filter((m) => m.role === 'ROLE_USER');
      } else if (activeTab === 'owner') {
        result = result.filter((m) => m.role === 'ROLE_OWNER');
      } else if (activeTab === 'suspended') {
        result = result.filter((m) => m.status === 'SUSPENDED');
      }

      // 상단 드롭다운 상태 필터 적용
      if (currentStatusFilter !== 'ALL') {
        result = result.filter((m) => m.status === currentStatusFilter);
      }
    } else {
      // '탈퇴 회원' 탭일 때는 오직 WITHDRAWN 상태인 회원만 노출
      result = result.filter((m) => m.status === 'WITHDRAWN');
    }

    return result;
  }, [activeTab, currentStatusFilter, originMemberList]);

  // 필터링된 결과 개수에 맞춰 하단 총 페이지 수를 계산
  const totalPages = useMemo(() => {
    const pages = Math.ceil(filteredList.length / PAGE_SIZE);
    return pages === 0 ? 1 : pages;
  }, [filteredList]);

  // 현재 페이지 번호(0, 1...)에 맞춰 최종 테이블에 10개씩만 슬라이스해서 보여줍니다.
  const pagedMemberList = useMemo(() => {
    const start = currentPage * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    return filteredList.slice(start, end);
  }, [currentPage, filteredList]);

  // 탭이나 상단 필터가 바뀌면 무조건 페이지를 1페이지(0)로 초기화
  useEffect(() => {
    setCurrentPage(0);
    setSelectedIds([]);
  }, [activeTab, currentStatusFilter]);

  const handleSelectRow = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  // 전체 선택은 현재 눈에 보이는 페이지의 10개 기준 처리
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

  // 정지/해제 API (단건/일괄 모두 처리)
  const handleToggleSuspend = (targetId, targetName, currentStatus) => {
    const idsToProcess = targetId ? [targetId] : selectedIds;
    if (idsToProcess.length === 0) {
      alert('대상을 선택해 주세요.');
      return;
    }

    // 단건 처리일 때 현재 상태에 따라 멘트와 URL 분기
    // 일괄 처리(상단 바 버튼)일 때는 기본적으로 '정지'로 작동하게 설정
    const isRelease = targetId && currentStatus === 'SUSPENDED';

    const confirmMessage = isRelease
      ? `[${targetName}] 회원의 정지를 해제하시겠습니까?`
      : targetId
        ? `[${targetName}] 회원을 정지하시겠습니까?`
        : `선택한 ${idsToProcess.length}명의 회원을 정말로 정지하시겠습니까?`;

    if (!window.confirm(confirmMessage)) return;

    const requests = idsToProcess.map((accountId) => {
      const url = isRelease
        ? `http://localhost:8080/admin/accounts/${accountId}/activate` // 정지 해제 API
        : `http://localhost:8080/admin/accounts/${accountId}/suspend`; // 정지 API
      return axios.patch(
        url,
        {},
        {
          headers,
        },
      );
    });

    axios
      .all(requests)
      .then(() => {
        alert(
          isRelease
            ? `${targetName} 회원의 정지가 해제되었습니다.`
            : '처리가 완료되었습니다.',
        );
        setSelectedIds([]);
        fetchAllMembers();
      })
      .catch((error) => {
        console.error('정지/해제 처리 중 에러:', error);
        alert('요청 처리 중 오류가 발생했습니다.');
      });
  };

  // 강제 탈퇴 API
  const handleActionWithdraw = (accountId, name) => {
    if (!window.confirm(`[${name}] 회원을 정말로 강제 탈퇴시키겠습니까?`))
      return;
    axios
      .delete(`http://localhost:8080/admin/accounts/${accountId}`, {
        headers,
      })
      .then(() => {
        alert(`${name} 회원이 탈퇴 처리되었습니다.`);
        fetchAllMembers();
      })
      .catch((error) => alert('탈퇴 처리 중 오류가 발생했습니다.'));
  };

  // 탈퇴 복구 API
  const handleRestoreMember = (accountId, name) => {
    if (
      !window.confirm(
        `[${name}] 회원의 탈퇴를 취소하고 계정을 복구하시겠습니까?`,
      )
    )
      return;

    axios
      .patch(
        `http://localhost:8080/admin/accounts/${accountId}/withdrawal/cancel`,
        {},
        {
          headers,
        },
      )
      .then((response) => {
        if (response.data && response.data.success) {
          alert(`${name} 회원의 탈퇴 해제(복구)가 완료되었습니다.`);
          fetchAllMembers();
        }
      })
      .catch((error) => {
        console.error('탈퇴 복구 실패:', error);
        alert(
          error.response?.data?.error?.message ||
            '탈퇴 해제 처리 중 오류가 발생했습니다.',
        );
      });
  };

  return (
    <div style={{ padding: '10px' }}>
      <MemberOverview total={tabCounts.all} suspended={tabCounts.suspended} />
      <MemberFilterBar
        selectedCount={selectedIds.length}
        onBulkSuspend={() => handleToggleSuspend()}
        onBulkActivate={() => handleActionWithdraw()}
        onApplyFilter={(status) => setCurrentStatusFilter(status)}
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
