import styled from 'styled-components';

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
  }
  tr:last-child td {
    border-bottom: none;
  }
`;

const RankBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  background: ${(props) => (props.$top ? '#edf5f1' : '#fafafa')};
  color: ${(props) => (props.$top ? '#2d5a43' : '#8c8c8c')};
`;

const CountText = styled.span`
  font-weight: 700;
  color: #2d5a43;
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

function FavoriteStatTable({ stats, loading }) {
  if (loading) {
    return (
      <Card>
        <EmptyText>불러오는 중...</EmptyText>
      </Card>
    );
  }

  if (stats.length === 0) {
    return (
      <Card>
        <EmptyText>해당 기간에 찜 데이터가 없어요.</EmptyText>
      </Card>
    );
  }

  return (
    <Card>
      <TableWrapper>
        <Table>
          <thead>
            <tr>
              <th style={{ width: 48 }}>순위</th>
              <th>대상</th>
              <th>ID</th>
              <th>찜 수</th>
            </tr>
          </thead>
          <tbody>
            {stats.map((s, idx) => (
              <tr key={`${s.refType}-${s.refId}`}>
                <td>
                  <RankBadge $top={idx < 3}>{idx + 1}</RankBadge>
                </td>
                <td style={{ fontWeight: 600 }}>{s.refName || '-'}</td>
                <td style={{ color: '#8c8c8c' }}>#{s.refId}</td>
                <td>
                  <CountText>{s.favoriteCount.toLocaleString()}</CountText>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      </TableWrapper>
    </Card>
  );
}

export default FavoriteStatTable;
