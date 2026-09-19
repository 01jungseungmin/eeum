import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { Star } from 'lucide-react';
import { STORE_STATUS_LABEL } from '../../../constants/storeConstants';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
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

const StoreNameCell = styled.div`
  .name {
    font-weight: 700;
  }
  .category {
    font-size: 12px;
    color: #8c8c8c;
    margin-top: 2px;
  }
`;

const RatingCell = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
  color: #faad14;

  span {
    color: #262626;
  }
`;

const StatusPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => (props.$status === 'SUSPENDED' ? '#fff1f0' : '#edf5f1')};
  color: ${(props) => (props.$status === 'SUSPENDED' ? '#cf1322' : '#2d5a43')};
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

function StoreTable({ stores, loading }) {
  const navigate = useNavigate();

  return (
    <Card>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : stores.length === 0 ? (
        <EmptyText>조건에 맞는 상점이 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>상점명</th>
                <th>주소</th>
                <th>전화번호</th>
                <th>평점</th>
                <th>찜 / 리뷰</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {stores.map((store) => (
                <Row
                  key={store.storeId}
                  onClick={() => navigate(`/admin/stores/${store.storeId}`)}
                >
                  <td>
                    <StoreNameCell>
                      <div className="name">{store.name}</div>
                      <div className="category">
                        {store.categoryName || '-'}
                      </div>
                    </StoreNameCell>
                  </td>
                  <td>{store.address || '-'}</td>
                  <td>{store.phone || '-'}</td>
                  <td>
                    <RatingCell>
                      <Star
                        size={14}
                        fill="currentColor"
                      />
                      <span>{(store.rating ?? 0).toFixed(1)}</span>
                    </RatingCell>
                  </td>
                  <td>
                    {store.favoriteCount ?? 0} / {store.reviewCount ?? 0}
                  </td>
                  <td>
                    <StatusPill $status={store.status}>
                      {STORE_STATUS_LABEL[store.status] || store.status}
                    </StatusPill>
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

export default StoreTable;
