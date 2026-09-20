import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import { Package, Tag, Clock, CheckCircle2 } from 'lucide-react';
import UsedProductTable from '../../../components/admin/used/UsedProductTable';
import { usedProductApi } from '../../../api/admin/usedProductApi';
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

function UsedProductManagementPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  // 상단 카드와 목록 칩이 함께 쓰는 보기 필터 (현재 불러온 페이지 안에서만 적용)
  const [filter, setFilter] = useState('ALL');
  // 목록 API가 썸네일을 내려주지 않아(서버가 thumbnailUrl에 null 고정) 상세 응답의 이미지로 채운다
  const [thumbnails, setThumbnails] = useState({});
  const requestedThumbnailIds = useRef(new Set());
  const isMountedRef = useRef(true);

  const fetchProducts = useCallback(async () => {
    setLoading(true);
    try {
      const res = await usedProductApi.getUsedProducts({
        page,
        size: PAGE_SIZE,
      });
      if (res.data?.success) {
        setProducts(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
        setTotalElements(res.data.data.totalElements || 0);
      }
    } catch (error) {
      console.error('중고거래 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    queueMicrotask(() => fetchProducts());
  }, [fetchProducts]);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  // 썸네일이 없는 게시글은 상세 조회로 대표 이미지를 가져온다.
  // 이미 요청한 게시글은 다시 요청하지 않아서 페이지를 오가도 중복 호출이 없다.
  useEffect(() => {
    const targets = products.filter(
      (p) =>
        !p.thumbnailUrl && !requestedThumbnailIds.current.has(p.usedProductId),
    );

    targets.forEach(async (product) => {
      const id = product.usedProductId;
      requestedThumbnailIds.current.add(id);

      try {
        const res = await usedProductApi.getUsedProductDetail(id);
        const images = res.data?.data?.images || [];
        const url = (images.find((img) => img.thumbnail) || images[0])
          ?.imageUrl;

        if (url && isMountedRef.current) {
          setThumbnails((prev) => ({ ...prev, [id]: url }));
        }
      } catch (error) {
        // 이미지 하나 못 가져와도 목록은 그대로 보여준다 (아이콘으로 대체)
        console.error(`중고거래 썸네일 조회 실패 (${id}):`, error);
      }
    });
  }, [products]);

  const productsWithThumbnail = useMemo(
    () =>
      products.map((p) => ({
        ...p,
        thumbnailUrl: p.thumbnailUrl || thumbnails[p.usedProductId] || null,
      })),
    [products, thumbnails],
  );

  // 상태별 집계 API가 따로 없어 "이 페이지" 범위로만 계산한다.
  const pageStats = useMemo(() => {
    return {
      selling: products.filter((p) => p.status === 'SELLING').length,
      reserved: products.filter((p) => p.status === 'RESERVED').length,
      sold: products.filter((p) => p.status === 'SOLD').length,
    };
  }, [products]);

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
            <span>전체 상품</span>
            <h2>{totalElements.toLocaleString()}</h2>
            <p>전체 페이지 합계</p>
          </div>
          <div className="icon-wrapper">
            <Package size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
          $clickable
          $active={filter === 'SELLING'}
          onClick={() => setFilter('SELLING')}
        >
          <div className="info">
            <span>판매중</span>
            <h2>{pageStats.selling}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <Tag size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#ad6800"
          $clickable
          $active={filter === 'RESERVED'}
          onClick={() => setFilter('RESERVED')}
        >
          <div className="info">
            <span>예약중</span>
            <h2>{pageStats.reserved}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <Clock size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#f5f5f5"
          $iconColor="#8c8c8c"
          $clickable
          $active={filter === 'SOLD'}
          onClick={() => setFilter('SOLD')}
        >
          <div className="info">
            <span>판매완료</span>
            <h2>{pageStats.sold}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <CheckCircle2 size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <UsedProductTable
        products={productsWithThumbnail}
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

export default UsedProductManagementPage;
