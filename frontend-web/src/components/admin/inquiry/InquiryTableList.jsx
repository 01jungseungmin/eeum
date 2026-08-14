import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import styled from 'styled-components';
import { Search, ChevronDown } from 'lucide-react';
import {
  INQUIRY_STATUS,
  INQUIRY_STATUS_INFO,
  INQUIRY_CATEGORY_MAP,
  INQUIRY_CATEGORY_OPTIONS,
} from '../../../constants/inquiryConstants';

const MainCard = styled.div`
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
`;

const ControlRow = styled.div`
  display: flex;
  gap: 12px;
`;

const SearchWrapper = styled.div`
  position: relative;
  flex: 1;
`;

const SearchIcon = styled(Search)`
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: #9ca3af;
`;

const SearchInput = styled.input`
  width: 100%;
  height: 40px;
  padding-left: 40px;
  padding-right: 16px;
  background-color: #f3f4f6;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  color: #111827;
  outline: none;
  box-sizing: border-box;

  &::placeholder {
    color: #9ca3af;
  }
`;

const SelectWrapper = styled.div`
  position: relative;
  width: 150px;
`;

const StyledSelect = styled.select`
  width: 100%;
  height: 40px;
  padding: 0 36px 0 14px;
  background-color: #f3f4f6;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  color: #374151;
  appearance: none;
  cursor: pointer;
  outline: none;
  box-sizing: border-box;
`;

const ChevronDownIcon = styled(ChevronDown)`
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #6b7280;
  pointer-events: none;
`;

const TabsList = styled.div`
  display: inline-flex;
  gap: 4px;
  background-color: #f3f4f6;
  padding: 4px;
  border-radius: 9999px;
  width: fit-content;
`;

const TabButton = styled.button`
  border: none;
  background-color: ${(props) => (props.$active ? '#ffffff' : 'transparent')};
  color: ${(props) => (props.$active ? '#111827' : '#6b7280')};
  font-weight: ${(props) => (props.$active ? '600' : '500')};
  font-size: 13px;
  padding: 6px 16px;
  border-radius: 9999px;
  cursor: pointer;
  box-shadow: ${(props) =>
    props.$active ? '0 1px 2px rgba(0, 0, 0, 0.05)' : 'none'};
  transition: all 0.2s;
`;

const TableWrapper = styled.div`
  overflow-x: auto;
`;

const StyledTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  text-align: left;
`;

const TrHead = styled.tr`
  border-bottom: 1px solid #f3f4f6;
`;

const Th = styled.th`
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 700;
  color: #111827;
  white-space: nowrap;
`;

const TrBody = styled.tr`
  border-bottom: 1px solid #f9fafb;
  transition: background-color 0.2s;

  &:hover {
    background-color: #f9fafb;
  }
`;

const Td = styled.td`
  padding: 16px;
  font-size: 14px;
  color: #4b5563;
  vertical-align: middle;

  &.id {
    font-weight: 700;
    color: #111827;
  }

  &.date {
    color: #6b7280;
    font-size: 13px;
  }
`;

const CategoryBadge = styled.span`
  background-color: #f3f4f6;
  color: #4b5563;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  display: inline-block;
  white-space: nowrap;
`;

const TitleLink = styled(Link)`
  color: #111827;
  font-weight: 500;
  text-decoration: none;

  &:hover {
    color: #059669;
  }
`;

const StatusBadge = styled.span`
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 9999px;
  display: inline-block;
  white-space: nowrap;

  ${(props) =>
    props.$type === 'waiting' &&
    `
    background-color: #FEF3C7;
    color: #D97706;
  `}

  ${(props) =>
    props.$type === 'done' &&
    `
    background-color: #D1FAE5;
    color: #059669;
  `}

  ${(props) =>
    props.$type === 'processing' &&
    `
    background-color: #DBEAFE;
    color: #2563EB;
  `}
`;

const ActionButton = styled.button`
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    background-color: #f9fafb;
    border-color: #d1d5db;
  }
`;

const PaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
`;

const PageButton = styled.button`
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid #e5e7eb;
  background-color: #ffffff;
  border-radius: 6px;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
`;

const PageInfoText = styled.span`
  font-size: 13px;
  color: #4b5563;
`;

export default function InquiryTableList({
  inquiries = [],
  isLoading,
  pageInfo,
  onPageChange = () => {},
  // 외부에서 제어할 경우를 위한 Props (없어도 자체 동작)
  searchTerm: externalSearchTerm,
  onSearchChange: externalOnSearchChange,
  categoryFilter: externalCategoryFilter,
  onCategoryChange: externalOnCategoryChange,
}) {
  // --- 💡 필터링용 로컬 상태 (외부 Props가 없을 때 사용) ---
  const [localSearchTerm, setLocalSearchTerm] = useState('');
  const [localCategoryFilter, setLocalCategoryFilter] = useState('ALL');
  const [activeTab, setActiveTab] = useState('ALL'); // 상태 필터 탭 (ALL, PENDING, IN_PROGRESS, ANSWERED)

  // 제어 상태 및 핸들러 결정 (Props 우선, 없으면 로컬 State)
  const currentSearch =
    externalSearchTerm !== undefined ? externalSearchTerm : localSearchTerm;
  const currentCategory =
    externalCategoryFilter !== undefined
      ? externalCategoryFilter
      : localCategoryFilter;

  const handleSearchChange = (e) => {
    if (externalOnSearchChange) {
      externalOnSearchChange(e);
    } else {
      setLocalSearchTerm(e.target.value);
    }
  };

  const handleCategoryChange = (e) => {
    if (externalOnCategoryChange) {
      externalOnCategoryChange(e);
    } else {
      setLocalCategoryFilter(e.target.value);
    }
  };

  // --- 💡 핵심: 리스트 필터링 로직 ---
  const filteredInquiries = inquiries.filter((inquiry) => {
    // 1. 카테고리 필터링
    const matchesCategory =
      currentCategory === 'ALL' || inquiry.category === currentCategory;

    // 2. 검색어 필터링 (제목 또는 작성자 이름)
    const query = currentSearch.toLowerCase().trim();
    const matchesSearch =
      !query ||
      inquiry.title?.toLowerCase().includes(query) ||
      inquiry.writerName?.toLowerCase().includes(query);

    // 3. 상태 탭 필터링 (ALL, PENDING, IN_PROGRESS, ANSWERED)
    let matchesTab = true;
    if (activeTab === 'PENDING') {
      matchesTab = inquiry.status === INQUIRY_STATUS.PENDING;
    } else if (activeTab === 'IN_PROGRESS') {
      matchesTab = inquiry.status === INQUIRY_STATUS.IN_PROGRESS;
    } else if (activeTab === 'ANSWERED') {
      matchesTab =
        inquiry.status === INQUIRY_STATUS.ANSWERED ||
        inquiry.status === INQUIRY_STATUS.COMPLETED;
    }

    return matchesCategory && matchesSearch && matchesTab;
  });

  // 날짜 포맷팅 함수 (YYYY.MM.DD HH:mm)
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
      date.getDate(),
    ).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(
      date.getMinutes(),
    ).padStart(2, '0')}`;
  };

  // 상태 뱃지 렌더링
  const renderStatusBadge = (status) => {
    const info = INQUIRY_STATUS_INFO[status] || {
      label: status,
      type: 'waiting',
    };
    return <StatusBadge $type={info.type}>{info.label}</StatusBadge>;
  };

  return (
    <MainCard>
      {/* 검색 및 필터 컨트롤 */}
      <ControlRow>
        <SearchWrapper>
          <SearchIcon size={16} />
          <SearchInput
            placeholder="제목, 작성자 검색"
            value={currentSearch}
            onChange={handleSearchChange}
          />
        </SearchWrapper>

        {/* 카테고리 셀렉트 */}
        <SelectWrapper>
          <StyledSelect
            value={currentCategory}
            onChange={handleCategoryChange}
          >
            {INQUIRY_CATEGORY_OPTIONS.map((opt) => (
              <option
                key={opt.value}
                value={opt.value}
              >
                {opt.label}
              </option>
            ))}
          </StyledSelect>
          <ChevronDownIcon size={16} />
        </SelectWrapper>
      </ControlRow>

      {/* 💡 상태별 탭 버튼 (클릭 시 해당 상태만 필터링) */}
      <TabsList>
        <TabButton
          $active={activeTab === 'ALL'}
          onClick={() => setActiveTab('ALL')}
        >
          전체 {inquiries.length}건
        </TabButton>
        <TabButton
          $active={activeTab === 'PENDING'}
          onClick={() => setActiveTab('PENDING')}
        >
          처리대기
        </TabButton>
        <TabButton
          $active={activeTab === 'IN_PROGRESS'}
          onClick={() => setActiveTab('IN_PROGRESS')}
        >
          처리중
        </TabButton>
        <TabButton
          $active={activeTab === 'ANSWERED'}
          onClick={() => setActiveTab('ANSWERED')}
        >
          답변완료
        </TabButton>
      </TabsList>

      {/* 테이블 */}
      <TableWrapper>
        <StyledTable>
          <thead>
            <TrHead>
              <Th style={{ width: '70px' }}>번호</Th>
              <Th style={{ width: '110px' }}>카테고리</Th>
              <Th>제목</Th>
              <Th style={{ width: '100px' }}>작성자</Th>
              <Th style={{ width: '150px' }}>접수일시</Th>
              <Th style={{ width: '100px' }}>상태</Th>
              <Th style={{ width: '90px' }}></Th>
            </TrHead>
          </thead>
          <tbody>
            {isLoading ? (
              <TrBody>
                <Td
                  colSpan={7}
                  style={{
                    textAlign: 'center',
                    padding: '40px',
                    color: '#9ca3af',
                  }}
                >
                  로딩 중입니다...
                </Td>
              </TrBody>
            ) : filteredInquiries.length === 0 ? (
              <TrBody>
                <Td
                  colSpan={7}
                  style={{
                    textAlign: 'center',
                    padding: '40px',
                    color: '#9ca3af',
                  }}
                >
                  {inquiries.length === 0
                    ? '등록된 문의가 없습니다.'
                    : '조건에 일치하는 문의가 없습니다.'}
                </Td>
              </TrBody>
            ) : (
              // 💡 필터링된 배열(filteredInquiries)을 렌더링
              filteredInquiries.map((inquiry) => {
                const isAnswered =
                  inquiry.status === INQUIRY_STATUS.ANSWERED ||
                  inquiry.status === INQUIRY_STATUS.COMPLETED ||
                  (inquiry.answers && inquiry.answers.length > 0);

                const inquiryId = inquiry.inquiryId || inquiry.id;

                return (
                  <TrBody key={inquiryId}>
                    <Td className="id">#{inquiryId}</Td>
                    <Td>
                      <CategoryBadge>
                        {INQUIRY_CATEGORY_MAP[inquiry.category] ||
                          inquiry.category ||
                          '기타'}
                      </CategoryBadge>
                    </Td>
                    <Td>
                      <TitleLink to={`/admin/inquiry/${inquiryId}`}>
                        {inquiry.title}
                      </TitleLink>
                    </Td>
                    <Td>{inquiry.writerName || '익명'}</Td>
                    <Td className="date">{formatDate(inquiry.createdAt)}</Td>
                    <Td>{renderStatusBadge(inquiry.status)}</Td>
                    <Td style={{ textAlign: 'right' }}>
                      <Link to={`/admin/inquiry/${inquiryId}`}>
                        <ActionButton>
                          {isAnswered ? '상세보기' : '답변하기'}
                        </ActionButton>
                      </Link>
                    </Td>
                  </TrBody>
                );
              })
            )}
          </tbody>
        </StyledTable>
      </TableWrapper>

      {/* 페이지네이션 */}
      {pageInfo && pageInfo.totalPages > 1 && (
        <PaginationWrapper>
          <PageButton
            disabled={pageInfo.page === 0}
            onClick={() => onPageChange(pageInfo.page - 1)}
          >
            이전
          </PageButton>
          <PageInfoText>
            {pageInfo.page + 1} / {pageInfo.totalPages}
          </PageInfoText>
          <PageButton
            disabled={pageInfo.page + 1 >= pageInfo.totalPages}
            onClick={() => onPageChange(pageInfo.page + 1)}
          >
            다음
          </PageButton>
        </PaginationWrapper>
      )}
    </MainCard>
  );
}
