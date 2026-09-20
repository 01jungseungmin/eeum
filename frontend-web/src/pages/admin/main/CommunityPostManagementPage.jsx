import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { FileText, EyeOff, CheckCircle2, BarChart3 } from 'lucide-react';
import CommunityPostTable from '../../../components/admin/community/CommunityPostTable';
import { communityPostApi } from '../../../api/admin/communityPostApi';
import { clickableCardStyle } from '../../../components/common/cardFilterStyle';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const SummaryCard = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);

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
      color: #bfbfbf;
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

const PAGE_SIZE = 20;

function CommunityPostManagementPage() {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  // 상단 카드와 목록 칩이 함께 쓰는 보기 필터 (현재 불러온 페이지 안에서만 적용): ALL | NORMAL | HIDDEN
  const [filter, setFilter] = useState('ALL');

  const fetchPosts = useCallback(async () => {
    setLoading(true);
    try {
      const res = await communityPostApi.getPosts({ page, size: PAGE_SIZE });
      if (res.data?.success) {
        setPosts(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
        setTotalElements(res.data.data.totalElements || 0);
      }
    } catch (error) {
      console.error('커뮤니티 게시글 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    queueMicrotask(() => fetchPosts());
  }, [fetchPosts]);

  // 목록 API가 상태별 집계를 따로 안 주기 때문에, 전체 건수(totalElements)만 서버 값을
  // 그대로 쓰고 나머지는 "이 페이지" 범위임을 명시해서 계산한다 — 다른 페이지에 숨겨진
  // 게시글까지 합친 것처럼 보이지 않게 하기 위함.
  const pageStats = useMemo(() => {
    const hiddenCount = posts.filter((p) => p.hidden).length;
    const totalViews = posts.reduce((sum, p) => sum + (p.viewCount || 0), 0);
    return {
      visibleCount: posts.length - hiddenCount,
      hiddenCount,
      totalViews,
    };
  }, [posts]);

  return (
    <Container>
      <SummaryGrid>
        <SummaryCard
          $iconBg="#f0f5ff"
          $iconColor="#2f54eb"
          $clickable
          $active={filter === 'ALL'}
          onClick={() => setFilter('ALL')}
        >
          <div className="info">
            <span>전체 게시글</span>
            <h2>{totalElements.toLocaleString()}</h2>
            <p>전체 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <FileText size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
          $clickable
          $active={filter === 'NORMAL'}
          onClick={() => setFilter('NORMAL')}
        >
          <div className="info">
            <span>노출중</span>
            <h2>{pageStats.visibleCount}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fff1f0"
          $iconColor="#cf1322"
          $clickable
          $active={filter === 'HIDDEN'}
          onClick={() => setFilter('HIDDEN')}
        >
          <div className="info">
            <span>숨김 처리</span>
            <h2>{pageStats.hiddenCount}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <EyeOff size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#ad6800"
        >
          <div className="info">
            <span>총 조회수</span>
            <h2>{pageStats.totalViews.toLocaleString()}</h2>
            <p>이 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <BarChart3 size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <CommunityPostTable
        posts={posts}
        loading={loading}
        totalElements={totalElements}
        filter={filter}
        onFilterChange={setFilter}
      />

      {totalPages > 1 && (
        <PaginationContainer>
          <PageButton
            disabled={page === 0}
            onClick={() => setPage((prev) => prev - 1)}
          >
            &lt;
          </PageButton>
          {Array.from({ length: totalPages }, (_, index) => (
            <PageButton
              key={index}
              $active={page === index}
              onClick={() => setPage(index)}
            >
              {index + 1}
            </PageButton>
          ))}
          <PageButton
            disabled={page === totalPages - 1}
            onClick={() => setPage((prev) => prev + 1)}
          >
            &gt;
          </PageButton>
        </PaginationContainer>
      )}
    </Container>
  );
}

export default CommunityPostManagementPage;
