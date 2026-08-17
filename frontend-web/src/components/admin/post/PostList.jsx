import React, { useState, useRef, useEffect } from 'react';
import styled from 'styled-components';
import { Search, ChevronDown, MoreVertical, Check } from 'lucide-react';

// ---------------- Styled Components ----------------
const Container = styled.div`
  background-color: #ffffff;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
`;

const FilterSection = styled.div`
  display: flex;
  gap: 12px;
  margin-bottom: 20px;

  @media (max-width: 768px) {
    flex-direction: column;
  }
`;

const SearchInputWrapper = styled.div`
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;

  svg {
    position: absolute;
    left: 14px;
    color: #9ca3af;
  }
`;

const SearchInput = styled.input`
  width: 100%;
  padding: 10px 16px 10px 42px;
  background-color: #f3f4f6;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  outline: none;

  &::placeholder {
    color: #9ca3af;
  }
`;

const DropdownWrapper = styled.div`
  position: relative;
`;

const DropdownButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 18px;
  background-color: #f3f4f6;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  cursor: pointer;
  min-width: 130px;
  gap: 10px;
  transition: background-color 0.15s;

  &:hover {
    background-color: #e5e7eb;
  }

  svg {
    color: #9ca3af;
  }
`;

const DropdownMenu = styled.ul`
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 160px;
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
  padding: 6px;
  margin: 0;
  list-style: none;
  z-index: 50;
`;

const DropdownItem = styled.li`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: ${(props) => (props.$isSelected ? '600' : '500')};
  color: ${(props) => (props.$isSelected ? '#1f2937' : '#374151')};
  background-color: ${(props) =>
    props.$isSelected ? '#eef2f6' : 'transparent'};
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background-color: ${(props) => (props.$isSelected ? '#eef2f6' : '#f9fafb')};
  }

  svg {
    color: #4b5563;
  }
`;

const TabSection = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
`;

const TabButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  border: none;
  background-color: ${(props) => (props.$active ? '#111827' : '#f3f4f6')};
  color: ${(props) => (props.$active ? '#ffffff' : '#374151')};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
`;

const RedBadge = styled.span`
  background-color: #ef4444;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  text-align: left;
`;

const Th = styled.th`
  padding: 14px 12px;
  font-size: 13px;
  font-weight: 700;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
  white-space: nowrap;
`;

// 클릭 가능한 게시글 행
const ClickableTr = styled.tr`
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover {
    background-color: #f9fafb;
  }
`;

const Td = styled.td`
  padding: 16px 12px;
  font-size: 14px;
  color: #111827;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
`;

const Checkbox = styled.input.attrs({ type: 'checkbox' })`
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: #059669;
`;

const PostTitle = styled.span`
  font-weight: 600;
  color: #111827;

  &:hover {
    text-decoration: underline;
  }
`;

const CategoryTag = styled.span`
  display: inline-block;
  padding: 3px 8px;
  background-color: #f3f4f6;
  border-radius: 6px;
  font-size: 12px;
  color: #4b5563;
`;

const StatusBadge = styled.span`
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  background-color: ${(props) =>
    props.$status === '신고됨' ? '#FEE2E2' : '#DCFCE7'};
  color: ${(props) => (props.$status === '신고됨' ? '#DC2626' : '#16A34A')};
`;

const IconButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  color: #9ca3af;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;

  &:hover {
    color: #374151;
  }
`;

const EmptyRow = styled.td`
  text-align: center;
  padding: 40px;
  color: #9ca3af;
  font-size: 14px;
`;

// ---------------- Sample Data ----------------
const mockPosts = [
  {
    id: 5821,
    title: '【공구/기획】새 아이폰 16 미래봄 500원분 입니다',
    author: '김민지',
    authorEmail: 'minji.kim@email.com',
    category: '중고거래',
    views: 412,
    comments: 23,
    likes: 2,
    status: '활성',
    date: '10분 전',
    content: '게시글 내용이 표시됩니다.',
  },
  {
    id: 5820,
    title: '톰북 심액 보건 교차사이 미래봄 인건',
    author: '박지훈',
    authorEmail: 'jihoon.park@email.com',
    category: '일반',
    views: 198,
    comments: 14,
    likes: 0,
    status: '활성',
    date: '10분 전',
    content: '톰북 심액 보건 관련 게시글 상세 내용입니다.',
  },
  {
    id: 5819,
    title: '【참회】아이 뭘나 사이거래 수수권업 곤란',
    author: '이서연',
    authorEmail: 'seoyeon.lee@email.com',
    category: '일반',
    views: 87,
    comments: 8,
    likes: 0,
    status: '활성',
    date: '10분 전',
    content: '참회 관련 상세 내용입니다.',
  },
  {
    id: 5817,
    title: '★★ 신축 빌라 보라색션 ★★ 즉시 가능 기능 ★★★',
    author: '정하윤',
    authorEmail: 'hayoon.jung@email.com',
    category: '부동산',
    views: 12,
    comments: 0,
    likes: 5,
    status: '신고됨',
    date: '10분 전',
    content: '신축 빌라 상세 매물 정보 내용입니다.',
  },
];

const boardOptions = ['전체 게시판', '일반', '중고거래', '부동산'];
const statusOptions = ['전체 상태', '활성', '신고됨'];

// ---------------- Main Component ----------------
const PostList = ({ onSelectPost }) => {
  const [activeTab, setActiveTab] = useState('전체');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedBoard, setSelectedBoard] = useState('전체 게시판');
  const [selectedStatus, setSelectedStatus] = useState('전체 상태');

  const [isBoardOpen, setIsBoardOpen] = useState(false);
  const [isStatusOpen, setIsStatusOpen] = useState(false);

  const boardRef = useRef(null);
  const statusRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (boardRef.current && !boardRef.current.contains(event.target)) {
        setIsBoardOpen(false);
      }
      if (statusRef.current && !statusRef.current.contains(event.target)) {
        setIsStatusOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const filteredPosts = mockPosts.filter((post) => {
    const matchesSearch =
      post.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      post.author.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesBoard =
      selectedBoard === '전체 게시판' || post.category === selectedBoard;

    const matchesStatus =
      selectedStatus === '전체 상태' || post.status === selectedStatus;

    let matchesTab = true;
    if (activeTab === '신고된 글') matchesTab = post.status === '신고됨';

    return matchesSearch && matchesBoard && matchesStatus && matchesTab;
  });

  return (
    <Container>
      <FilterSection>
        <SearchInputWrapper>
          <Search size={18} />
          <SearchInput
            placeholder="제목, 작성자, 내용 검색"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </SearchInputWrapper>

        <DropdownWrapper ref={boardRef}>
          <DropdownButton onClick={() => setIsBoardOpen((prev) => !prev)}>
            {selectedBoard}
            <ChevronDown size={16} />
          </DropdownButton>
          {isBoardOpen && (
            <DropdownMenu>
              {boardOptions.map((option) => (
                <DropdownItem
                  key={option}
                  $isSelected={selectedBoard === option}
                  onClick={() => {
                    setSelectedBoard(option);
                    setIsBoardOpen(false);
                  }}
                >
                  {option}
                  {selectedBoard === option && <Check size={16} />}
                </DropdownItem>
              ))}
            </DropdownMenu>
          )}
        </DropdownWrapper>

        <DropdownWrapper ref={statusRef}>
          <DropdownButton onClick={() => setIsStatusOpen((prev) => !prev)}>
            {selectedStatus}
            <ChevronDown size={16} />
          </DropdownButton>
          {isStatusOpen && (
            <DropdownMenu>
              {statusOptions.map((option) => (
                <DropdownItem
                  key={option}
                  $isSelected={selectedStatus === option}
                  onClick={() => {
                    setSelectedStatus(option);
                    setIsStatusOpen(false);
                  }}
                >
                  {option}
                  {selectedStatus === option && <Check size={16} />}
                </DropdownItem>
              ))}
            </DropdownMenu>
          )}
        </DropdownWrapper>
      </FilterSection>

      <TabSection>
        <TabButton
          $active={activeTab === '전체'}
          onClick={() => setActiveTab('전체')}
        >
          전체 8,242건
        </TabButton>
        <TabButton
          $active={activeTab === '신고된 글'}
          onClick={() => setActiveTab('신고된 글')}
        >
          신고된 글 <RedBadge>7</RedBadge>
        </TabButton>
        <TabButton
          $active={activeTab === '댓글 관리'}
          onClick={() => setActiveTab('댓글 관리')}
        >
          댓글 관리 23
        </TabButton>
        <TabButton
          $active={activeTab === '자동 필터'}
          onClick={() => setActiveTab('자동 필터')}
        >
          자동 필터 12
        </TabButton>
      </TabSection>

      <Table>
        <thead>
          <tr>
            <Th style={{ width: '40px' }}>
              <Checkbox />
            </Th>
            <Th style={{ width: '80px' }}>번호</Th>
            <Th>제목</Th>
            <Th style={{ width: '90px' }}>작성자</Th>
            <Th style={{ width: '90px' }}>게시판</Th>
            <Th style={{ width: '60px' }}>조회</Th>
            <Th style={{ width: '60px' }}>댓글</Th>
            <Th style={{ width: '60px' }}>좋아요</Th>
            <Th style={{ width: '80px' }}>상태</Th>
            <Th style={{ width: '90px' }}>작성일</Th>
            <Th style={{ width: '40px' }}></Th>
          </tr>
        </thead>
        <tbody>
          {filteredPosts.length > 0 ? (
            filteredPosts.map((post) => (
              <ClickableTr
                key={post.id}
                onClick={() => onSelectPost && onSelectPost(post)}
              >
                <Td onClick={(e) => e.stopPropagation()}>
                  <Checkbox />
                </Td>
                <Td style={{ color: '#6B7280' }}>{post.id}</Td>
                <Td>
                  <PostTitle>{post.title}</PostTitle>
                </Td>
                <Td>{post.author}</Td>
                <Td>
                  <CategoryTag>{post.category}</CategoryTag>
                </Td>
                <Td>{post.views}</Td>
                <Td>{post.comments}</Td>
                <Td>{post.likes}</Td>
                <Td>
                  <StatusBadge $status={post.status}>{post.status}</StatusBadge>
                </Td>
                <Td style={{ color: '#6B7280', fontSize: '13px' }}>
                  {post.date}
                </Td>
                <Td onClick={(e) => e.stopPropagation()}>
                  <IconButton>
                    <MoreVertical size={16} />
                  </IconButton>
                </Td>
              </ClickableTr>
            ))
          ) : (
            <tr>
              <EmptyRow colSpan={11}>조건에 맞는 게시글이 없습니다.</EmptyRow>
            </tr>
          )}
        </tbody>
      </Table>
    </Container>
  );
};

export default PostList;
