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
  const [currentStatusFilter, setCurrentStatusFilter] = useState('ALL'); // 'ALL', 'ACTIVE', 'SUSPENDED'
  const [selectedIds, setSelectedIds] = useState([]);
  const [memberList, setMemberList] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    totalElements: 0,
    suspendedCount: 0,
  });

  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const fetchMembers = () => {
    const token =
      localStorage.getItem('accessToken') ||
      sessionStorage.getItem('accessToken');

    axios
      .get('http://localhost:8080/admin/accounts', {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
      })
      .then((response) => {
        if (response.data && response.data.success) {
          const apiData = response.data.data;

          setMemberList(apiData.content || []);

          const total = apiData.totalElements || apiData.content.length;
          const suspended = (apiData.content || []).filter(
            (m) => m.status === 'SUSPENDED',
          ).length;

          setPageInfo({
            totalElements: total,
            suspendedCount: suspended,
          });
        }
      })
      .catch((error) => {
        console.error('멤버 데이터 로드 실패:', error);
      });
  };

  useEffect(() => {
    fetchMembers(currentPage);
  }, [currentPage]);

  useEffect(() => {
    setCurrentPage(0);
    setSelectedIds([]);
  }, [activeTab, currentStatusFilter]);

  const filteredMemberList = useMemo(() => {
    let result = [...memberList];

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
  }, [activeTab, currentStatusFilter, memberList]);

  useEffect(() => {
    setSelectedIds([]);
  }, [activeTab]);

  const handleSelectRow = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    );
  };

  // 상단 전체 헤더 체크박스 토글 핸들러
  const handleSelectAll = () => {
    if (selectedIds.length === memberList.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(memberList.map((m) => m.accountId));
    }
  };

  return (
    <div style={{ padding: '10px' }}>
      <MemberOverview
        total={pageInfo.totalElements}
        suspended={pageInfo.suspendedCount}
      />
      <MemberFilterBar
        selectedCount={selectedIds.length}
        onBulkSuspend={() => alert('선택한 회원을 정지합니다.')}
        onBulkActivate={() => alert('선택한 회원의 정지를 해제합니다.')}
        onApplyFilter={(status) => setCurrentStatusFilter(status)}
      />
      <MemberTabs
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        rawData={memberList}
      />
      <MemberTable
        data={filteredMemberList}
        selectedIds={selectedIds}
        onSelectRow={handleSelectRow}
        onSelectAll={handleSelectAll}
      />
      {totalPages > 0 && (
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
