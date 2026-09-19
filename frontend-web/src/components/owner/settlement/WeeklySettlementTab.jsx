import styled from 'styled-components';
import { WEEKLY_SETTLEMENT_STATUS_LABEL } from '../../../constants/settlementConstants';

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

const StatusPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => {
    if (props.$status === 'COMPLETED') return '#edf5f1';
    if (props.$status === 'FAILED') return '#fff1f0';
    if (props.$status === 'MANUAL_REVIEW_REQUIRED') return '#fffbe6';
    return '#e6f7ff';
  }};
  color: ${(props) => {
    if (props.$status === 'COMPLETED') return '#2d5a43';
    if (props.$status === 'FAILED') return '#cf1322';
    if (props.$status === 'MANUAL_REVIEW_REQUIRED') return '#ad6800';
    return '#1d6fc9';
  }};
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const won = (value) => `${Math.round(Number(value || 0)).toLocaleString()}원`;
const formatDate = (value) =>
  value ? new Date(value).toLocaleDateString('ko-KR') : '-';

function WeeklySettlementTab({ weeklySettlements, loading }) {
  return (
    <Card>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : weeklySettlements.length === 0 ? (
        <EmptyText>정산 내역이 아직 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>정산 기간</th>
                <th>정산 금액</th>
                <th>상태</th>
                <th>지급 완료일</th>
              </tr>
            </thead>
            <tbody>
              {weeklySettlements.map((s) => (
                <tr key={s.weeklySettlementId}>
                  <td>
                    {formatDate(s.periodStartAt)} ~ {formatDate(s.periodEndAt)}
                  </td>
                  <td>
                    <strong>{won(s.payoutAmount)}</strong>
                  </td>
                  <td>
                    <StatusPill $status={s.status}>
                      {WEEKLY_SETTLEMENT_STATUS_LABEL[s.status] || s.status}
                    </StatusPill>
                  </td>
                  <td>{formatDate(s.payoutCompletedAt)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </TableWrapper>
      )}
    </Card>
  );
}

export default WeeklySettlementTab;
