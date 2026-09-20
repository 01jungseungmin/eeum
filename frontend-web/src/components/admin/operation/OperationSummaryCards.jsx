import styled from 'styled-components';
import { AlertTriangle, MessageCircle, Flag, Clock } from 'lucide-react';
import { OPERATION_FAILURE_CATEGORY_LABEL } from '../../../constants/operationConstants';

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const CardLabel = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #8c8c8c;
  font-weight: 600;
`;

const CardValue = styled.div`
  font-size: 26px;
  font-weight: 700;
  color: ${(props) => props.$color || '#262626'};
`;

const BreakdownCard = styled(Card)`
  grid-column: 1 / -1;
`;

const BreakdownRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
`;

const BreakdownPill = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  font-size: 12px;
  color: #595959;

  strong {
    color: #262626;
  }
`;

const SinceText = styled.div`
  font-size: 12px;
  color: #bfbfbf;
`;

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

function OperationSummaryCards({ summary }) {
  if (!summary) return null;

  return (
    <Grid>
      <Card>
        <CardLabel>
          <Flag size={14} />
          미처리 신고
        </CardLabel>
        <CardValue $color={summary.pendingReportCount > 0 ? '#f5222d' : undefined}>
          {summary.pendingReportCount.toLocaleString()}건
        </CardValue>
      </Card>

      <Card>
        <CardLabel>
          <MessageCircle size={14} />
          미답변 문의
        </CardLabel>
        <CardValue $color={summary.pendingInquiryCount > 0 ? '#d97706' : undefined}>
          {summary.pendingInquiryCount.toLocaleString()}건
        </CardValue>
      </Card>

      <Card>
        <CardLabel>
          <AlertTriangle size={14} />
          집계 구간 내 실패
        </CardLabel>
        <CardValue $color={summary.failureCount > 0 ? '#f5222d' : undefined}>
          {summary.failureCount.toLocaleString()}건
        </CardValue>
        <SinceText>
          <Clock
            size={11}
            style={{ verticalAlign: 'middle', marginRight: 4 }}
          />
          {formatDate(summary.failureCountSince)} 이후
        </SinceText>
      </Card>

      <BreakdownCard>
        <CardLabel>카테고리별 실패 건수</CardLabel>
        <BreakdownRow>
          {Object.entries(summary.failureCountByCategory || {}).map(
            ([category, count]) => (
              <BreakdownPill key={category}>
                {OPERATION_FAILURE_CATEGORY_LABEL[category] || category}
                <strong>{count}</strong>
              </BreakdownPill>
            ),
          )}
        </BreakdownRow>
      </BreakdownCard>
    </Grid>
  );
}

export default OperationSummaryCards;
