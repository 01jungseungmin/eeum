import styled from 'styled-components';
import { NOTIFICATION_TYPE_FILTER_GROUPS, NOTIFICATION_TYPE_LABEL } from '../../../constants/notificationConstants';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
`;

const FilterRow = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
`;

const Select = styled.select`
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 14px;
  height: 40px;
  font-size: 13px;
  background: white;
  cursor: pointer;
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

const TypeTag = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: #f0f5ff;
  color: #2f54eb;
`;

const ContentCell = styled.div`
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #595959;
`;

const ReadPill = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => (props.$read ? '#edf5f1' : '#fffbe6')};
  color: ${(props) => (props.$read ? '#2d5a43' : '#ad6800')};
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

function NotificationHistoryTable({
  notifications,
  loading,
  category,
  onCategoryChange,
}) {
  return (
    <Card>
      <FilterRow>
        <Select
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
        >
          <option value="">전체 타입</option>
          {NOTIFICATION_TYPE_FILTER_GROUPS.map((group) => (
            <option
              key={group.label}
              value={group.label}
            >
              {group.label}
            </option>
          ))}
        </Select>
      </FilterRow>

      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : notifications.length === 0 ? (
        <EmptyText>발송 이력이 없어요.</EmptyText>
      ) : (
        <TableWrapper>
          <Table>
            <thead>
              <tr>
                <th>타입</th>
                <th>제목</th>
                <th>내용</th>
                <th>발송일시</th>
                <th>읽음 여부</th>
              </tr>
            </thead>
            <tbody>
              {notifications.map((n) => (
                <tr key={n.notificationId}>
                  <td>
                    <TypeTag>
                      {NOTIFICATION_TYPE_LABEL[n.type] || n.type}
                    </TypeTag>
                  </td>
                  <td>{n.title}</td>
                  <td>
                    <ContentCell>{n.content}</ContentCell>
                  </td>
                  <td>{formatDate(n.createdAt)}</td>
                  <td>
                    <ReadPill $read={n.read}>
                      {n.read ? '읽음' : '안읽음'}
                    </ReadPill>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </TableWrapper>
      )}
    </Card>
  );
}

export default NotificationHistoryTable;
