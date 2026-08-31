import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { Search, ChevronDown } from 'lucide-react';
import {
  TARGET_TYPE_MAP,
  REASON_MAP,
  STATUS_MAP,
  TYPE_TABS,
  STATUS_FILTER_OPTIONS,
} from '../../../constants/reportConstants';

const ReportList = ({
  reports = [],
  loading = false,
  page = 0,
  totalPages = 0,
  selectedStatus = 'ALL',
  onStatusChange,
  onPageChange,
}) => {
  const navigate = useNavigate();

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedTypeTab, setSelectedTypeTab] = useState('ALL');
  const [isCategoryOpen, setIsCategoryOpen] = useState(false);

  // 클라이언트 측 검색 및 유형 2차 필터링
  const filteredReports = reports.filter((item) => {
    const matchesSearch =
      item.content?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.reporterName?.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesType =
      selectedTypeTab === 'ALL' || item.targetType === selectedTypeTab;

    return matchesSearch && matchesType;
  });

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('ko-KR');
  };

  return (
    <TableWrapper>
      <FilterRow>
        <SearchBox>
          <Search
            size={16}
            color="#9CA3AF"
          />
          <input
            type="text"
            placeholder="신고 내용, 신고자 검색"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </SearchBox>

        <DropdownContainer>
          <DropdownButton onClick={() => setIsCategoryOpen(!isCategoryOpen)}>
            {
              STATUS_FILTER_OPTIONS.find((opt) => opt.value === selectedStatus)
                ?.label
            }{' '}
            <ChevronDown size={16} />
          </DropdownButton>
          {isCategoryOpen && (
            <DropdownMenu>
              {STATUS_FILTER_OPTIONS.map((opt) => (
                <DropdownItem
                  key={opt.value}
                  $isSelected={selectedStatus === opt.value}
                  onClick={() => {
                    onStatusChange(opt.value);
                    setIsCategoryOpen(false);
                  }}
                >
                  {opt.label}
                </DropdownItem>
              ))}
            </DropdownMenu>
          )}
        </DropdownContainer>
      </FilterRow>

      <CategoryTabs>
        {TYPE_TABS.map((tab) => (
          <TabButton
            key={tab.value}
            $active={selectedTypeTab === tab.value}
            onClick={() => setSelectedTypeTab(tab.value)}
          >
            {tab.label}
          </TabButton>
        ))}
      </CategoryTabs>

      <Table>
        <thead>
          <tr>
            <th>ID</th>
            <th>유형</th>
            <th>신고 사유</th>
            <th>신고자</th>
            <th>접수일</th>
            <th>상태</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td
                colSpan="7"
                style={{ textAlign: 'center', padding: '32px' }}
              >
                로딩 중입니다...
              </td>
            </tr>
          ) : filteredReports.length > 0 ? (
            filteredReports.map((item) => (
              <tr key={item.reportId}>
                <td>#{item.reportId}</td>
                <td>
                  <Badge $gray>
                    {TARGET_TYPE_MAP[item.targetType] || item.targetType}
                  </Badge>
                </td>
                <td>{REASON_MAP[item.reason] || item.reason}</td>
                <td>{item.reporterName}</td>
                <td>{formatDate(item.createdAt)}</td>
                <td>
                  <Badge $status={item.status}>
                    {STATUS_MAP[item.status] || item.status}
                  </Badge>
                </td>
                <td>
                  <ActionButton onClick={() => navigate(`./${item.reportId}`)}>
                    처리하기
                  </ActionButton>
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td
                colSpan="7"
                style={{
                  textAlign: 'center',
                  padding: '32px',
                  color: '#9CA3AF',
                }}
              >
                신고 내역이 없습니다.
              </td>
            </tr>
          )}
        </tbody>
      </Table>

      {totalPages > 1 && (
        <PaginationContainer>
          <PageButton
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
          >
            이전
          </PageButton>
          <span>
            {page + 1} / {totalPages}
          </span>
          <PageButton
            disabled={page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
          >
            다음
          </PageButton>
        </PaginationContainer>
      )}
    </TableWrapper>
  );
};

export default ReportList;

const TableWrapper = styled.div`
  background: white;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  padding: 20px;
`;

const FilterRow = styled.div`
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
`;

const SearchBox = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f3f4f6;
  padding: 10px 14px;
  border-radius: 8px;
  input {
    border: none;
    background: transparent;
    width: 100%;
    outline: none;
    font-size: 14px;
  }
`;

const DropdownContainer = styled.div`
  position: relative;
`;

const DropdownButton = styled.button`
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f3f4f6;
  border: none;
  padding: 0 16px;
  height: 100%;
  border-radius: 8px;
  font-size: 14px;
  color: #374151;
  cursor: pointer;
`;

const DropdownMenu = styled.div`
  position: absolute;
  top: 110%;
  right: 0;
  width: 160px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  z-index: 10;
  padding: 4px;
`;

const DropdownItem = styled.div`
  padding: 8px 12px;
  font-size: 14px;
  border-radius: 6px;
  cursor: pointer;
  background: ${(props) => (props.$isSelected ? '#e5e7eb' : 'transparent')};
  color: #374151;
  &:hover {
    background: #f3f4f6;
  }
`;

const CategoryTabs = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
`;

const TabButton = styled.button`
  background: ${(props) => (props.$active ? '#ffffff' : '#f3f4f6')};
  color: ${(props) => (props.$active ? '#111827' : '#6b7280')};
  border: ${(props) =>
    props.$active ? '1px solid #111827' : '1px solid transparent'};
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: ${(props) => (props.$active ? '600' : '400')};
  cursor: pointer;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  th,
  td {
    padding: 14px 12px;
    text-align: left;
    font-size: 14px;
    border-bottom: 1px solid #f3f4f6;
  }
  th {
    color: #374151;
    font-weight: 600;
  }
  td {
    color: #4b5563;
  }
`;

const Badge = styled.span`
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  background: ${(props) => {
    if (props.$status === 'PENDING') return '#FEF08A';
    if (props.$status === 'REVIEWED') return '#BBF7D0';
    if (props.$status === 'DISMISSED') return '#E5E7EB';
    return '#F3F4F6';
  }};
  color: ${(props) => {
    if (props.$status === 'PENDING') return '#854D0E';
    if (props.$status === 'REVIEWED') return '#166534';
    if (props.$status === 'DISMISSED') return '#374151';
    return '#374151';
  }};
`;

const ActionButton = styled.button`
  border: 1px solid #e5e7eb;
  background: white;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  &:hover {
    background: #f9fafb;
  }
`;

const PaginationContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  font-size: 14px;
`;

const PageButton = styled.button`
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;
