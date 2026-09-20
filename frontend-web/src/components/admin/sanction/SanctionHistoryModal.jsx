import { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { X } from 'lucide-react';
import { sanctionApi } from '../../../api/admin/sanctionApi';
import {
  SANCTION_ACTION_LABEL,
  SANCTION_SOURCE_LABEL,
} from '../../../constants/sanctionConstants';

const Overlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ModalBox = styled.div`
  background: #ffffff;
  width: 100%;
  max-width: 640px;
  max-height: 80vh;
  border-radius: 16px;
  padding: 28px;
  box-shadow:
    0 20px 25px -5px rgba(0, 0, 0, 0.1),
    0 8px 10px -6px rgba(0, 0, 0, 0.1);
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 24px;
  right: 24px;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
`;

const Title = styled.h2`
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
`;

const Subtitle = styled.p`
  font-size: 13px;
  color: #64748b;
  margin: -12px 0 0 0;
`;

const TableWrapper = styled.div`
  overflow-y: auto;
  flex: 1;
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
    position: sticky;
    top: 0;
    background: white;
  }
  td {
    padding: 12px;
    border-bottom: 1px solid #f5f5f5;
    color: #262626;
    vertical-align: top;
  }
  tr:last-child td {
    border-bottom: none;
  }
`;

const ActionTag = styled.span`
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: ${(props) => (props.$isRelease ? '#edf5f1' : '#fff1f0')};
  color: ${(props) => (props.$isRelease ? '#2d5a43' : '#f5222d')};
`;

const NoteText = styled.div`
  color: #595959;
  white-space: pre-wrap;
`;

const EmptyText = styled.div`
  text-align: center;
  padding: 40px 0;
  color: #bfbfbf;
  font-size: 13px;
`;

const PaginationRow = styled.div`
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

const formatDate = (value) =>
  value ? new Date(value).toLocaleString('ko-KR') : '-';

const PAGE_SIZE = 20;

// targetType: 'ACCOUNT' | 'STORE' — 백엔드가 대상별로 조회 API를 분리해뒀다 (전체 목록 API는 없음)
function SanctionHistoryModal({ targetType, targetId, targetLabel, onClose }) {
  const [histories, setHistories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const fetchHistories = useCallback(async () => {
    setLoading(true);
    try {
      const res =
        targetType === 'STORE'
          ? await sanctionApi.getStoreHistories(targetId, page, PAGE_SIZE)
          : await sanctionApi.getAccountHistories(targetId, page, PAGE_SIZE);

      if (res.data?.success) {
        setHistories(res.data.data.content || []);
        setTotalPages(res.data.data.totalPages || 1);
      }
    } catch (error) {
      console.error('제재 이력 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [targetType, targetId, page]);

  useEffect(() => {
    queueMicrotask(() => fetchHistories());
  }, [fetchHistories]);

  return (
    <Overlay onClick={onClose}>
      <ModalBox onClick={(e) => e.stopPropagation()}>
        <CloseButton onClick={onClose}>
          <X size={20} />
        </CloseButton>
        <Title>제재 이력</Title>
        <Subtitle>{targetLabel}</Subtitle>

        {loading ? (
          <EmptyText>불러오는 중...</EmptyText>
        ) : histories.length === 0 ? (
          <EmptyText>제재 이력이 없어요.</EmptyText>
        ) : (
          <TableWrapper>
            <Table>
              <thead>
                <tr>
                  <th>조치</th>
                  <th>사유 출처</th>
                  <th>관리자 메모</th>
                  <th>처리 일시</th>
                </tr>
              </thead>
              <tbody>
                {histories.map((h) => (
                  <tr key={h.sanctionHistoryId}>
                    <td>
                      <ActionTag $isRelease={h.action === 'ACTIVATE'}>
                        {SANCTION_ACTION_LABEL[h.action] || h.action}
                      </ActionTag>
                    </td>
                    <td>
                      {SANCTION_SOURCE_LABEL[h.source] || h.source}
                      {h.sourceReportId ? ` (신고 #${h.sourceReportId})` : ''}
                    </td>
                    <td>
                      <NoteText>{h.adminNote || '-'}</NoteText>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {formatDate(h.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </TableWrapper>
        )}

        {totalPages > 1 && (
          <PaginationRow>
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
          </PaginationRow>
        )}
      </ModalBox>
    </Overlay>
  );
}

export default SanctionHistoryModal;
