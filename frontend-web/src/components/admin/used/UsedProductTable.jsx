import { useMemo, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { EyeOff, ImageOff } from 'lucide-react';
import {
  USED_PRODUCT_STATUS_LABEL,
  formatUsedProductPrice,
} from '../../../constants/usedProductConstants';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const TopBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
`;

const CountText = styled.div`
  font-size: 14px;
  color: #595959;

  strong {
    color: #262626;
    font-weight: 700;
  }
`;

const FilterChips = styled.div`
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
`;

const Chip = styled.button`
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid ${(props) => (props.$active ? '#2d5a43' : '#e0e0e0')};
  background: ${(props) => (props.$active ? '#2d5a43' : 'white')};
  color: ${(props) => (props.$active ? 'white' : '#595959')};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: #2d5a43;
    color: ${(props) => (props.$active ? 'white' : '#2d5a43')};
  }
`;

const TableWrapper = styled.div`
  overflow-x: auto;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    text-align: left;
    padding: 10px 12px;
    color: #8c8c8c;
    font-weight: 600;
    border-bottom: 1px solid #f0f0f0;
    white-space: nowrap;
  }
  td {
    padding: 12px;
    border-bottom: 1px solid #f5f5f5;
    color: #262626;
    white-space: nowrap;
  }
  tr:last-child td {
    border-bottom: none;
  }
`;

const Row = styled.tr`
  cursor: pointer;
  &:hover td {
    background: #fafafa;
  }
`;

const PostCell = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 320px;

  .img-box {
    width: 44px;
    height: 44px;
    border-radius: 10px;
    background: #f1f5f9;
    color: #b0b7c0;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    flex-shrink: 0;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .title {
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

const StatusPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => {
    if (props.$status === 'SOLD') return '#f5f5f5';
    if (props.$status === 'RESERVED') return '#fffbe6';
    return '#edf5f1';
  }};
  color: ${(props) => {
    if (props.$status === 'SOLD') return '#8c8c8c';
    if (props.$status === 'RESERVED') return '#ad6800';
    return '#2d5a43';
  }};
`;

const HiddenPill = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #fff1f0;
  color: #cf1322;
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const formatDate = (value) =>
  value ? new Date(value).toLocaleDateString('ko-KR') : '-';

const FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'SELLING', label: '판매중' },
  { value: 'RESERVED', label: '예약중' },
  { value: 'SOLD', label: '판매완료' },
  { value: 'HIDDEN', label: '숨김' },
];

function UsedProductTable({ products, loading, totalElements = 0 }) {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('ALL');

  const filtered = useMemo(() => {
    if (filter === 'ALL') return products;
    if (filter === 'HIDDEN') return products.filter((p) => p.hidden);
    return products.filter((p) => p.status === filter);
  }, [products, filter]);

  return (
    <Card>
      <TopBar>
        <CountText>
          전체 <strong>{totalElements.toLocaleString()}</strong>건 · 이 페이지{' '}
          <strong>{filtered.length}</strong>건 표시 중
        </CountText>
        <FilterChips>
          {FILTERS.map((f) => (
            <Chip
              key={f.value}
              $active={filter === f.value}
              onClick={() => setFilter(f.value)}
            >
              {f.label}
            </Chip>
          ))}
        </FilterChips>
      </TopBar>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : filtered.length === 0 ? (
        <EmptyText>조건에 맞는 중고거래 게시글이 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>게시글</th>
                <th>카테고리</th>
                <th>가격</th>
                <th>동네</th>
                <th>찜 / 조회</th>
                <th>등록일</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <Row
                  key={p.usedProductId}
                  onClick={() => navigate(`/admin/used/${p.usedProductId}`)}
                >
                  <td>
                    <PostCell>
                      <div className="img-box">
                        {p.thumbnailUrl ? (
                          <img
                            src={p.thumbnailUrl}
                            alt={p.title}
                          />
                        ) : (
                          <ImageOff size={16} />
                        )}
                      </div>
                      <span className="title">{p.title}</span>
                    </PostCell>
                  </td>
                  <td>{p.categoryName || '-'}</td>
                  <td>{formatUsedProductPrice(p.priceType, p.price)}</td>
                  <td>{p.regionName || '-'}</td>
                  <td>
                    {p.favoriteCount ?? 0} / {p.viewCount ?? 0}
                  </td>
                  <td>{formatDate(p.createdAt)}</td>
                  <td>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <StatusPill $status={p.status}>
                        {USED_PRODUCT_STATUS_LABEL[p.status] || p.status}
                      </StatusPill>
                      {p.hidden && (
                        <HiddenPill>
                          <EyeOff size={11} />
                          숨김
                        </HiddenPill>
                      )}
                    </div>
                  </td>
                </Row>
              ))}
            </tbody>
          </Table>
        </TableWrapper>
      )}
    </Card>
  );
}

export default UsedProductTable;
