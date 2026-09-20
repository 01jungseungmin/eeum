import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { dashboardApi } from '../../../api/admin/dashboardApi';
import { REASON_MAP } from '../../../constants/reportConstants';
import { INQUIRY_CATEGORY_MAP } from '../../../constants/inquiryConstants';
import { formatRelativeTime, isWaitingOver } from '../../../utils/relativeTime';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }
`;

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ActionItem = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;

  .left-side {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .icon-avatar {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: #edf5f1;
    color: #2d5a43;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
  }
  .info {
    .title {
      font-size: 13px;
      font-weight: 600;
      color: #262626;
    }
    .desc {
      font-size: 11px;
      color: #bfbfbf;
      margin-top: 2px;
    }
  }
  .right-side {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .action-link {
    font-size: 12px;
    font-weight: 600;
    color: #595959;
    display: flex;
    align-items: center;
    cursor: pointer;
    &:hover {
      color: #2d5a43;
    }
  }
`;

const StatusTag = styled.span`
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 600;
  background-color: ${(props) =>
    props.$type === '긴급' ? '#fff5f5' : '#f5f5f5'};
  color: ${(props) => (props.$type === '긴급' ? '#ff4d4f' : '#595959')};
`;

const EmptyText = styled.div`
  padding: 30px 0;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
`;

// 분류별 건수 맵({ SPAM: 1, ABUSE: 2, ... })에서 건수가 많은 순으로 상위 N개를 "라벨 N건" 으로 요약
const summarizeCounts = (countMap, labelMap, limit = 2) => {
  return Object.entries(countMap || {})
    .filter(([, count]) => count > 0)
    .sort(([, a], [, b]) => b - a)
    .slice(0, limit)
    .map(([key, count]) => `${labelMap[key] || key} ${count}건`);
};

const joinDesc = (parts) => parts.filter(Boolean).join(' · ');

// API 응답 → 화면 항목. 대기 건이 없으면 안내 문구만 보여주고 태그/링크는 숨긴다.
const buildItems = (data) => {
  const { ownerApprovals, reports, inquiries } = data;

  const approvalCount = ownerApprovals?.count ?? 0;
  const approvalOthers = approvalCount - 1;

  return [
    {
      id: 'approval',
      icon: '📋',
      title: `사장 가입 승인 ${approvalCount}건`,
      count: approvalCount,
      desc: joinDesc([
        ownerApprovals?.latestStoreName
          ? `${ownerApprovals.latestStoreName}${approvalOthers > 0 ? ` 외 ${approvalOthers}건` : ''}`
          : '',
        formatRelativeTime(ownerApprovals?.latestRequestedAt),
      ]),
      urgent: isWaitingOver(ownerApprovals?.oldestRequestedAt),
      path: '/admin/approval',
    },
    {
      id: 'report',
      icon: '🚨',
      title: `신고 처리 ${reports?.count ?? 0}건`,
      count: reports?.count ?? 0,
      desc: joinDesc([
        ...summarizeCounts(reports?.countByReason, REASON_MAP),
        formatRelativeTime(reports?.latestReportedAt),
      ]),
      urgent: isWaitingOver(reports?.oldestReportedAt),
      path: '/admin/reports',
    },
    {
      id: 'inquiry',
      icon: '💬',
      title: `고객 문의 ${inquiries?.count ?? 0}건`,
      count: inquiries?.count ?? 0,
      desc: joinDesc([
        ...summarizeCounts(inquiries?.countByCategory, INQUIRY_CATEGORY_MAP),
        formatRelativeTime(inquiries?.latestCreatedAt),
      ]),
      urgent: isWaitingOver(inquiries?.oldestCreatedAt),
      path: '/admin/inquiry',
    },
  ];
};

function AdminPendingActions() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let isMounted = true;

    const fetchPendingActions = async () => {
      try {
        const response = await dashboardApi.getPendingActions();
        if (isMounted && response.data?.success) {
          setItems(buildItems(response.data.data));
        }
      } catch (err) {
        console.error('처리 대기 항목 조회 실패:', err);
        if (isMounted) setError(true);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchPendingActions();

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <Card>
      <Header>
        <h3>처리 대기 항목</h3>
      </Header>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : error ? (
        <EmptyText>처리 대기 항목을 불러오지 못했어요.</EmptyText>
      ) : (
        <ListContainer>
          {items.map((item) => (
            <ActionItem key={item.id}>
              <div className="left-side">
                <div className="icon-avatar">{item.icon}</div>
                <div className="info">
                  <div className="title">{item.title}</div>
                  <div className="desc">
                    {item.count > 0 ? item.desc : '대기 중인 건이 없어요'}
                  </div>
                </div>
              </div>
              {item.count > 0 && (
                <div className="right-side">
                  <StatusTag $type={item.urgent ? '긴급' : '일반'}>
                    {item.urgent ? '긴급' : '일반'}
                  </StatusTag>
                  <div
                    className="action-link"
                    onClick={() => navigate(item.path)}
                  >
                    처리하기 <ChevronRight size={12} />
                  </div>
                </div>
              )}
            </ActionItem>
          ))}
        </ListContainer>
      )}
    </Card>
  );
}

export default AdminPendingActions;
