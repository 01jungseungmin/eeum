import React, { useState } from 'react';
import styled from 'styled-components';
import { Search } from 'lucide-react';
import ApprovalItem from './ApprovalItem';

const SectionContainer = styled.div`
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.01);
`;

const FilterBar = styled.div`
  display: flex;
  gap: 12px;

  .search-wrapper {
    flex: 1;
    position: relative;
    display: flex;
    align-items: center;

    input {
      width: 100%;
      padding: 12px 16px 12px 42px;
      border: 1px solid #e8e8e8;
      border-radius: 8px;
      font-size: 13px;
      background-color: #f5f6f7;
      outline: none;
      transition: all 0.2s;
      &:focus {
        border-color: #2d5a43;
        background-color: white;
      }
    }
    .search-icon {
      position: absolute;
      left: 16px;
      color: #a0a0a0;
    }
  }

  .btn-search {
    background: white;
    color: #262626;
    border: 1px solid #d9d9d9;
    padding: 0 22px;
    border-radius: 8px;
    font-weight: 600;
    font-size: 13px;
    cursor: pointer;
    &:hover {
      background-color: #f5f5f5;
      border-color: #bfbfbf;
    }
  }
`;

const ListWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

function AdminApprovalListContainer({ listData, onApprove, onReject }) {
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [selectedId, setSelectedId] = useState(null);

  const safeList = listData || [];

  const handleSearchSubmit = () => {
    setSubmittedKeyword(keyword);
  };

  // 검색어 필터링 로직
  const filteredList = safeList.filter(
    (account) =>
      account.name?.toLowerCase().includes(submittedKeyword.toLowerCase()) ||
      account.email?.toLowerCase().includes(submittedKeyword.toLowerCase()) ||
      account.nickname?.toLowerCase().includes(submittedKeyword.toLowerCase()),
  );

  return (
    <SectionContainer>
      <FilterBar>
        <div className="search-wrapper">
          <Search className="search-icon" size={16} />
          <input
            type="text"
            placeholder="신청자명, 닉네임, 이메일로 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSearchSubmit();
            }}
          />
        </div>
        <button className="btn-search" onClick={handleSearchSubmit}>
          검색
        </button>
      </FilterBar>

      <ListWrapper>
        {filteredList.length === 0 ? (
          <div
            style={{
              textAlign: 'center',
              padding: '40px 0',
              color: '#8c8c8c',
              fontSize: '14px',
            }}
          >
            가입 승인 대기 내역이 존재하지 않습니다.
          </div>
        ) : (
          filteredList.map((account) => (
            <ApprovalItem
              key={account.ownerInfo?.ownerInfoId}
              account={account}
              isSelected={selectedId === account.ownerInfo?.ownerInfoId}
              onSelect={() => setSelectedId(account.ownerInfo?.ownerInfoId)}
              onApprove={onApprove}
              onReject={onReject}
            />
          ))
        )}
      </ListWrapper>
    </SectionContainer>
  );
}

export default AdminApprovalListContainer;
