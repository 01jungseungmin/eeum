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

  const fetchAllMembers = () => {
    axios
      .get('http://localhost:8080/admin/accounts?page=0&size=1000', {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
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
    return {
      all: originMemberList.length,
      general: originMemberList.filter((m) => m.role === 'ROLE_USER').length,
      owner: originMemberList.filter((m) => m.role === 'ROLE_OWNER').length,
      suspended: originMemberList.filter((m) => m.status === 'SUSPENDED')
        .length,
    };
  }, [originMemberList]);

  const filteredList = useMemo(() => {
    let result = [...originMemberList];

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

    return result;
  }, [activeTab, currentStatusFilter, originMemberList]);

  // 필터링된 결과 개수(예: 사장회원 클릭 시 414개)에 맞춰 하단 총 페이지 수를 계산합니다.
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

  const handleBulkSuspend = () => {
    if (selectedIds.length === 0) {
      alert('정지할 회원을 한 명 이상 선택해 주세요.');
      return;
    }
    if (
      !window.confirm(
        `선택한 ${selectedIds.length}명의 회원을 정말로 정지하시겠습니까?`,
      )
    ) {
      return;
    }

    const requests = selectedIds.map((accountId) =>
      axios.patch(
        `http://localhost:8080/admin/accounts/${accountId}/suspend`,
        {},
        {
          headers: {
            Authorization: token ? `Bearer ${token}` : '',
          },
        },
      ),
    );
    axios
      .all(requests)
      .then(() => {
        alert('선택한 회원이 모두 정지 처리되었습니다.');
        setSelectedIds([]);
        fetchAllMembers();
      })
      .catch((error) => {
        console.error('회원 정지 처리 중 에러 발생:', error);

        const serverError = error.response?.data?.error;
        if (serverError) {
          alert(`실패 원인: ${serverError.message} (${serverError.code})`);
        } else {
          alert('회원 정지 처리 중 알 수 없는 에러가 발생했습니다.');
        }
      });
  };

  return (
    <div style={{ padding: '10px' }}>
      <MemberOverview total={tabCounts.all} suspended={tabCounts.suspended} />
      <MemberFilterBar
        selectedCount={selectedIds.length}
        onBulkSuspend={handleBulkSuspend}
        onBulkActivate={() => alert('선택한 회원의 정지를 해제합니다.')}
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
