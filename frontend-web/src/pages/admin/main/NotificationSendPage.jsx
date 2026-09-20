import { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { Megaphone, Bell, BellRing, MailOpen } from 'lucide-react';
import NotificationSendModal from '../../../components/admin/notification/NotificationSendModal';
import NotificationHistoryTable from '../../../components/admin/notification/NotificationHistoryTable';
import { notificationApi } from '../../../api/admin/notificationApi';
import { NOTIFICATION_TYPE_FILTER_GROUPS } from '../../../constants/notificationConstants';
import { clickableCardStyle } from '../../../components/common/cardFilterStyle';

const Container = styled.div`
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
`;

const SummaryCard = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);

  ${clickableCardStyle}
  .info {
    span {
      font-size: 12px;
      color: #8c8c8c;
      font-weight: 500;
    }
    h2 {
      margin: 8px 0 4px 0;
      font-size: 26px;
      font-weight: 700;
      color: #262626;
    }
    p {
      margin: 0;
      font-size: 12px;
      color: #bfbfbf;
      font-weight: 600;
    }
  }
  .icon-wrapper {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: ${(props) => props.$iconBg};
    color: ${(props) => props.$iconColor};
  }
`;

const ActionRow = styled.div`
  display: flex;
  gap: 12px;
`;

const ActionButton = styled.button`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-radius: 10px;
  border: 1px solid #2d5a43;
  background: white;
  color: #2d5a43;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    background: #edf5f1;
  }

  &.primary {
    background: #2d5a43;
    color: white;

    &:hover {
      background: #244a37;
    }
  }
`;

const PaginationContainer = styled.div`
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

const PAGE_SIZE = 20;
// 백엔드는 대분류(카테고리) 필터를 지원하지 않고 단일 타입만 받으므로,
// 카테고리 선택 시 소속 타입들을 병렬로 이만큼씩 가져와 프론트에서 합쳐 페이징한다.
const CATEGORY_FETCH_SIZE = 100;

function NotificationSendPage() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [sendModalType, setSendModalType] = useState(null); // 'system' | 'event' | null
  // 상단 카드로 고르는 보기 필터 (현재 불러온 페이지 안에서만 적용)
  // ALL | UNREAD | READ | SYSTEM_NOTICE | MARKETING_EVENT — 같은 카드를 다시 누르면 해제된다
  const [cardFilter, setCardFilter] = useState('ALL');
  const toggleCardFilter = (next) =>
    setCardFilter((prev) => (prev === next ? 'ALL' : next));

  const fetchHistory = useCallback(async () => {
    setLoading(true);
    try {
      const group = NOTIFICATION_TYPE_FILTER_GROUPS.find(
        (g) => g.label === category,
      );

      if (!group) {
        const res = await notificationApi.getHistory({ page, size: PAGE_SIZE });
        if (res.data?.success) {
          setNotifications(res.data.data.content || []);
          setTotalPages(res.data.data.totalPages || 1);
        }
        return;
      }

      const responses = await Promise.all(
        group.types.map((t) =>
          notificationApi.getHistory({
            type: t,
            page: 0,
            size: CATEGORY_FETCH_SIZE,
          }),
        ),
      );
      const merged = responses
        .flatMap((res) =>
          res.data?.success ? res.data.data.content || [] : [],
        )
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

      setTotalPages(Math.max(1, Math.ceil(merged.length / PAGE_SIZE)));
      setNotifications(
        merged.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE),
      );
    } catch (error) {
      console.error('알림 발송 이력 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  }, [page, category]);

  useEffect(() => {
    queueMicrotask(() => fetchHistory());
  }, [fetchHistory]);

  const handleCategoryChange = (next) => {
    setCategory(next);
    setPage(0);
  };

  // 카테고리 필터에 따라 조회 방식이 달라져 전체 합계를 신뢰할 수 없으므로
  // "이 페이지" 범위로만 집계한다.
  const pageStats = useMemo(() => {
    return {
      unread: notifications.filter((n) => !n.read).length,
      read: notifications.filter((n) => n.read).length,
      systemNotice: notifications.filter((n) => n.type === 'SYSTEM_NOTICE')
        .length,
      marketingEvent: notifications.filter(
        (n) => n.type === 'MARKETING_EVENT',
      ).length,
    };
  }, [notifications]);

  const displayedNotifications = useMemo(() => {
    switch (cardFilter) {
      case 'UNREAD':
        return notifications.filter((n) => !n.read);
      case 'READ':
        return notifications.filter((n) => n.read);
      case 'SYSTEM_NOTICE':
      case 'MARKETING_EVENT':
        return notifications.filter((n) => n.type === cardFilter);
      default:
        return notifications;
    }
  }, [notifications, cardFilter]);

  return (
    <Container>
      <SummaryGrid>
        <SummaryCard
          $iconBg="#fffbe6"
          $iconColor="#ad6800"
          $clickable
          $active={cardFilter === 'UNREAD'}
          onClick={() => toggleCardFilter('UNREAD')}
        >
          <div className="info">
            <span>안읽음</span>
            <h2>{pageStats.unread}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <BellRing size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#edf5f1"
          $iconColor="#2d5a43"
          $clickable
          $active={cardFilter === 'READ'}
          onClick={() => toggleCardFilter('READ')}
        >
          <div className="info">
            <span>읽음</span>
            <h2>{pageStats.read}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <MailOpen size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#f0f5ff"
          $iconColor="#2f54eb"
          $clickable
          $active={cardFilter === 'SYSTEM_NOTICE'}
          onClick={() => toggleCardFilter('SYSTEM_NOTICE')}
        >
          <div className="info">
            <span>시스템 공지</span>
            <h2>{pageStats.systemNotice}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <Bell size={16} />
          </div>
        </SummaryCard>
        <SummaryCard
          $iconBg="#fff0f6"
          $iconColor="#c41d7f"
          $clickable
          $active={cardFilter === 'MARKETING_EVENT'}
          onClick={() => toggleCardFilter('MARKETING_EVENT')}
        >
          <div className="info">
            <span>마케팅/이벤트</span>
            <h2>{pageStats.marketingEvent}</h2>
            <p>이 페이지 기준</p>
          </div>
          <div className="icon-wrapper">
            <Megaphone size={16} />
          </div>
        </SummaryCard>
      </SummaryGrid>

      <ActionRow>
        <ActionButton
          className="primary"
          onClick={() => setSendModalType('system')}
        >
          <Bell size={16} />
          시스템 공지 발송
        </ActionButton>
        <ActionButton onClick={() => setSendModalType('event')}>
          <Megaphone size={16} />
          이벤트/마케팅 알림 발송
        </ActionButton>
      </ActionRow>

      <NotificationHistoryTable
        notifications={displayedNotifications}
        loading={loading}
        category={category}
        onCategoryChange={handleCategoryChange}
      />

      {totalPages > 1 && (
        <PaginationContainer>
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
        </PaginationContainer>
      )}

      {sendModalType && (
        <NotificationSendModal
          type={sendModalType}
          onClose={() => setSendModalType(null)}
          onSent={fetchHistory}
        />
      )}
    </Container>
  );
}

export default NotificationSendPage;
