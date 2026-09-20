import { useMemo } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { EyeOff } from 'lucide-react';

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

const TitleCell = styled.div`
  max-width: 300px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

const NormalPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #edf5f1;
  color: #2d5a43;
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
  { value: 'NORMAL', label: '노출중' },
  { value: 'HIDDEN', label: '숨김' },
];

// filter: 상단 카드와 칩이 함께 쓰는 보기 필터(ALL | NORMAL | HIDDEN)를 페이지가 들고 있다
function CommunityPostTable({
  posts,
  loading,
  totalElements = 0,
  filter = 'ALL',
  onFilterChange = () => {},
}) {
  const navigate = useNavigate();

  const filtered = useMemo(() => {
    if (filter === 'ALL') return posts;
    if (filter === 'HIDDEN') return posts.filter((p) => p.hidden);
    return posts.filter((p) => !p.hidden);
  }, [posts, filter]);

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
              onClick={() => onFilterChange(f.value)}
            >
              {f.label}
            </Chip>
          ))}
        </FilterChips>
      </TopBar>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : filtered.length === 0 ? (
        <EmptyText>조건에 맞는 게시글이 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>제목</th>
                <th>작성자</th>
                <th>카테고리</th>
                <th>지역</th>
                <th>조회 / 좋아요 / 댓글</th>
                <th>작성일</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <Row
                  key={p.postId}
                  onClick={() => navigate(`/admin/community/posts/${p.postId}`)}
                >
                  <td>
                    <TitleCell>{p.title}</TitleCell>
                  </td>
                  <td>{p.authorNickname || '-'}</td>
                  <td>{p.categoryName || '-'}</td>
                  <td>{p.regionName || '-'}</td>
                  <td>
                    {p.viewCount ?? 0} / {p.likeCount ?? 0} / {p.commentCount ?? 0}
                  </td>
                  <td>{formatDate(p.createdAt)}</td>
                  <td>
                    {p.hidden ? (
                      <HiddenPill>
                        <EyeOff size={11} />
                        숨김
                      </HiddenPill>
                    ) : (
                      <NormalPill>노출중</NormalPill>
                    )}
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

export default CommunityPostTable;
