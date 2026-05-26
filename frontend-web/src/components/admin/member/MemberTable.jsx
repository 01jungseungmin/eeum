import React from 'react';
import styled from 'styled-components';
import { MoreVertical } from 'lucide-react';

const TableContainer = styled.div`
  background: white;
  border: 1px solid #e8e8e8;
  border-top: none;
  border-radius: 0 0 16px 16px;
  overflow: hidden;
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 14px;

  th,
  td {
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;
    vertical-align: middle;
  }

  th {
    background: #fafafa;
    color: #555;
    font-weight: 600;
    font-size: 13px;
  }

  tr:last-child td {
    border-bottom: none;
  }

  input[type='checkbox'] {
    width: 16px;
    height: 16px;
    accent-color: #2d5a43;
    cursor: pointer;
  }
`;

const AvatarCircle = styled.div`
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 13px;
  background: ${(props) => (props.$isOwner ? '#e8f5e9' : '#ffe0b2')};
  color: ${(props) => (props.$isOwner ? '#2e7d32' : '#f57c00')};
`;

const TypeBadge = styled.span`
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid #d9d9d9;
  background: #fafafa;
  color: #555;
`;

const StatusBadge = styled.span`
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  background: ${(props) => (props.$isActive ? '#e8f5e9' : '#fff1f0')};
  color: ${(props) => (props.$isActive ? '#2e7d32' : '#f5222d')};
`;

const ActionButton = styled.button`
  background: transparent;
  border: none;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  &:hover {
    color: #333;
  }
`;

function MemberTable({ data, selectedIds, onSelectRow, onSelectAll }) {
  const isAllSelected =
    data.length > 0 && data.every((row) => selectedIds.includes(row.accountId));

  const formatDate = (isoString) => {
    if (!isoString) return '-';
    const date = new Date(isoString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(
      2,
      '0',
    )}.${String(date.getDate()).padStart(2, '0')}`;
  };

  return (
    <TableContainer>
      <Table>
        <thead>
          <tr>
            <th style={{ width: '40px' }}>
              <input
                type="checkbox"
                checked={isAllSelected}
                onChange={onSelectAll}
              />
            </th>
            <th>구분</th>
            <th>회원</th>
            <th>이메일</th>
            <th>가입 유형</th>
            <th>지역 ID</th>
            <th>가입일자</th>
            <th>상태</th>
            <th style={{ width: '40px' }}></th>
          </tr>
        </thead>
        <tbody>
          {data.map((row) => {
            const isOwner = row.role === 'ROLE_OWNER';
            const isActive = row.status === 'ACTIVE';

            return (
              <tr key={row.accountId}>
                <td>
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(row.accountId)}
                    onChange={() => onSelectRow(row.accountId)}
                  />
                </td>
                <td>
                  <AvatarCircle $isOwner={isOwner}>
                    {row.name ? row.name.charAt(0) : 'U'}
                  </AvatarCircle>
                </td>
                <td style={{ fontWeight: '600', color: '#262626' }}>
                  {row.name}
                </td>
                <td style={{ color: '#595959' }}>{row.email}</td>
                <td>
                  <TypeBadge>{isOwner ? '사장' : '일반'}</TypeBadge>
                </td>
                <td style={{ color: '#595959' }}>
                  {row.primaryRegionId
                    ? `지역 ${row.primaryRegionId}`
                    : '미지정'}
                </td>
                <td style={{ color: '#595959' }}>
                  {formatDate(row.createdAt)}
                </td>
                <td>
                  <StatusBadge $isActive={isActive}>
                    {isActive ? '활성' : '정지'}
                  </StatusBadge>
                </td>
                <td>
                  <ActionButton>
                    <MoreVertical size={16} />
                  </ActionButton>
                </td>
              </tr>
            );
          })}
        </tbody>
      </Table>
    </TableContainer>
  );
}

export default MemberTable;
