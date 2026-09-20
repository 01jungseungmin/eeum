import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { dashboardApi } from '../../../api/admin/dashboardApi';
import { ACTIVITY_TITLE } from '../../../constants/dashboardConstants';
import { formatRelativeTime } from '../../../utils/relativeTime';

const Card = styled.div`
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
`;

const Header = styled.div`
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #262626;
  }
`;

const TimelineContainer = styled.div`
  display: flex;
  flex-direction: column;
`;

const TimelineRow = styled.div`
  display: flex;
  gap: 14px;
  padding-bottom: 20px;
  position: relative;
  &:last-child {
    padding-bottom: 0;
  }
`;

const TimelineDot = styled.div`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2d5a43;
  margin-top: 5px;
  position: relative;
  z-index: 2;
  &::after {
    content: '';
    position: absolute;
    top: 8px;
    left: 3px;
    width: 2px;
    height: 48px;
    background: #f0f0f0;
    z-index: 1;
  }
  ${TimelineRow}:last-child &::after {
    display: none;
  }
`;

const TimelineContent = styled.div`
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  .text-side {
    h4 {
      font-size: 13px;
      font-weight: 600;
      margin: 0 0 2px 0;
      color: #262626;
    }
    p {
      font-size: 12px;
      color: #8c8c8c;
      margin: 0;
    }
  }
  .time-text {
    font-size: 11px;
    color: #bfbfbf;
  }
`;

const EmptyText = styled.div`
  padding: 30px 0;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
`;

// 실시간 활동은 새로고침 없이도 갱신되도록 주기적으로 다시 조회한다
const REFRESH_INTERVAL_MS = 30 * 1000;
const ACTIVITY_LIMIT = 6;

// 활동 종류별 부가 설명 — 결제는 금액과 상점명을 함께 보여준다
const buildDescription = (activity) => {
  const { type, description, amount } = activity;

  if (type === 'PAYMENT_COMPLETED') {
    const price = `₩${Number(amount || 0).toLocaleString()} 결제 확인`;
    return description ? `${price} · ${description}` : price;
  }
  if (type === 'MEMBER_SIGNUP') {
    return description ? `${description}(신규 유저)` : '동네 인증 전 신규 유저';
  }
  return description || '';
};

function AdminLiveActivities() {
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let isMounted = true;

    const fetchActivities = async () => {
      try {
        const response = await dashboardApi.getActivities({
          limit: ACTIVITY_LIMIT,
        });
        if (isMounted && response.data?.success) {
          setActivities(response.data.data || []);
          setError(false);
        }
      } catch (err) {
        console.error('실시간 활동 조회 실패:', err);
        if (isMounted) setError(true);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchActivities();
    const timerId = setInterval(fetchActivities, REFRESH_INTERVAL_MS);

    return () => {
      isMounted = false;
      clearInterval(timerId);
    };
  }, []);

  return (
    <Card>
      <Header>
        <h3>실시간 활동</h3>
      </Header>
      {loading ? (
        <EmptyText>불러오는 중...</EmptyText>
      ) : error && activities.length === 0 ? (
        <EmptyText>실시간 활동을 불러오지 못했어요.</EmptyText>
      ) : activities.length === 0 ? (
        <EmptyText>아직 표시할 활동이 없어요.</EmptyText>
      ) : (
        <TimelineContainer>
          {activities.map((act) => (
            <TimelineRow key={`${act.type}-${act.targetId}-${act.occurredAt}`}>
              <TimelineDot />
              <TimelineContent>
                <div className="text-side">
                  <h4>{act.title || ACTIVITY_TITLE[act.type]}</h4>
                  <p>{buildDescription(act)}</p>
                </div>
                <span className="time-text">
                  {formatRelativeTime(act.occurredAt)}
                </span>
              </TimelineContent>
            </TimelineRow>
          ))}
        </TimelineContainer>
      )}
    </Card>
  );
}

export default AdminLiveActivities;
