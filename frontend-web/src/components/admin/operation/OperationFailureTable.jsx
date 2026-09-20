import { Fragment, useState } from 'react';
import styled from 'styled-components';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { OPERATION_FAILURE_CATEGORY_LABEL } from '../../../constants/operationConstants';

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
  tr.clickable {
    cursor: pointer;
  }
  tr.clickable:hover td {
    background: #fafafa;
  }
`;

const CategoryTag = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #fff1f0;
  color: #f5222d;
  white-space: nowrap;
`;

const EllipsisCell = styled.div`
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #595959;
`;

const RefCell = styled.div`
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
`;

const PayloadRow = styled.tr`
  td {
    background: #fafafa;
  }
`;

const PayloadBox = styled.pre`
  margin: 0;
  padding: 12px;
  background: #1f1f1f;
  color: #d9d9d9;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

function OperationFailureTable({ failures, loading }) {
  const [expandedId, setExpandedId] = useState(null);

  if (loading) {
    return (
      <Card>
        <EmptyText>불러오는 중...</EmptyText>
      </Card>
    );
  }

  if (failures.length === 0) {
    return (
      <Card>
        <EmptyText>실패 이력이 없어요.</EmptyText>
      </Card>
    );
  }

  return (
    <Card>
      <TableWrapper>
        <Table>
          <thead>
            <tr>
              <th style={{ width: 24 }}></th>
              <th>분류</th>
              <th>작업명</th>
              <th>대상</th>
              <th>에러코드</th>
              <th>에러 메시지</th>
              <th>발생일시</th>
            </tr>
          </thead>
          <tbody>
            {failures.map((f) => {
              const isExpanded = expandedId === f.failureLogId;
              return (
                <Fragment key={f.failureLogId}>
                  <tr
                    className="clickable"
                    onClick={() =>
                      setExpandedId(isExpanded ? null : f.failureLogId)
                    }
                  >
                    <td>
                      {isExpanded ? (
                        <ChevronDown size={14} />
                      ) : (
                        <ChevronRight size={14} />
                      )}
                    </td>
                    <td>
                      <CategoryTag>
                        {OPERATION_FAILURE_CATEGORY_LABEL[f.category] ||
                          f.category}
                      </CategoryTag>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>{f.operation}</td>
                    <td>
                      <RefCell>
                        {f.refType || '-'} {f.refId ? `#${f.refId}` : ''}
                      </RefCell>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {f.errorCode || '-'}
                    </td>
                    <td>
                      <EllipsisCell title={f.errorMessage}>
                        {f.errorMessage || '-'}
                      </EllipsisCell>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {formatDate(f.occurredAt)}
                    </td>
                  </tr>
                  {isExpanded && (
                    <PayloadRow>
                      <td colSpan={7}>
                        <PayloadBox>{f.payload || '(payload 없음)'}</PayloadBox>
                      </td>
                    </PayloadRow>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </Table>
      </TableWrapper>
    </Card>
  );
}

export default OperationFailureTable;
