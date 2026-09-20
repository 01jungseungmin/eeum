import { useCallback, useEffect, useState } from 'react';
import { settlementApi } from '../api/owner/settlementApi';
import { OWNER_REVENUE_EXCLUDED_FROM_TOTAL } from '../constants/settlementConstants';

const REVENUE_PAGE_SIZE = 100;
const WEEKLY_PAGE_SIZE = 50;
// 무한 루프 방지용 안전장치 — 한 매장이 이 이상 페이지를 갖는 경우는 사실상 없다고 가정
const MAX_PAGES = 20;
// 일별/월별 집계에 필요한 만큼만 가져오면 되므로, 그보다 오래된 데이터까지 전부
// 끌어올 필요는 없다 (매장당 주문량이 많아질수록 불필요한 페이지 호출이 늘어남)
const LOOKBACK_DAYS = 400;

// (page) => axios Promise 를 받아, 커서(cutoff) 이전 데이터가 나오거나 마지막 페이지에
// 도달할 때까지 순차적으로 모아준다. 각 API는 이미 고정된 정렬(createdAt/periodEndAt desc)로
// 내려주므로 별도 sort 파라미터는 넘기지 않는다.
const fetchAllPages = async (fetchPage, { cutoff, dateField } = {}) => {
  const items = [];

  for (let page = 0; page < MAX_PAGES; page += 1) {
    const res = await fetchPage(page);
    const body = res.data?.data;
    if (!body) break;

    const content = body.content || [];
    items.push(...content);

    const oldestInPage = content[content.length - 1];
    const reachedCutoff =
      cutoff && oldestInPage && new Date(oldestInPage[dateField]) < cutoff;

    if (body.last || content.length === 0 || reachedCutoff) break;
  }

  return items;
};

export const getStartOfWeek = (date = new Date()) => {
  const d = new Date(date);
  const day = d.getDay(); // 0=일 ~ 6=토
  const diffFromMonday = day === 0 ? 6 : day - 1;
  d.setDate(d.getDate() - diffFromMonday);
  d.setHours(0, 0, 0, 0);
  return d;
};

export const getStartOfMonth = (date = new Date()) => {
  const d = new Date(date.getFullYear(), date.getMonth(), 1);
  d.setHours(0, 0, 0, 0);
  return d;
};

const isCountableRevenue = (revenue) =>
  !OWNER_REVENUE_EXCLUDED_FROM_TOTAL.includes(revenue.status);

// 사장 매출/정산 페이지에 필요한 데이터를 한 번에 모아 가져오고,
// 요약 카드(이번 주 매출 / 정산 대기 / 이번 달 수수료 / 누적 정산 완료)까지 계산해서 내려준다.
export const useSettlementData = () => {
  const [revenues, setRevenues] = useState([]);
  const [weeklySettlements, setWeeklySettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const cutoff = new Date();
      cutoff.setDate(cutoff.getDate() - LOOKBACK_DAYS);

      const [revenueItems, weeklyItems] = await Promise.all([
        fetchAllPages(
          (page) =>
            settlementApi.getRevenues({ page, size: REVENUE_PAGE_SIZE }),
          { cutoff, dateField: 'createdAt' },
        ),
        fetchAllPages((page) =>
          settlementApi.getWeeklySettlements({ page, size: WEEKLY_PAGE_SIZE }),
        ),
      ]);

      setRevenues(revenueItems);
      setWeeklySettlements(weeklyItems);
    } catch (err) {
      console.error('정산 데이터 조회 실패:', err);
      setError('정산 데이터를 불러오지 못했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => load());
  }, [load]);

  const summary = calculateSummary(revenues, weeklySettlements);

  return { revenues, weeklySettlements, summary, loading, error, reload: load };
};

function calculateSummary(revenues, weeklySettlements) {
  const startOfWeek = getStartOfWeek();
  const startOfMonth = getStartOfMonth();

  const weekRevenue = revenues
    .filter(
      (r) => isCountableRevenue(r) && new Date(r.createdAt) >= startOfWeek,
    )
    .reduce((sum, r) => sum + Number(r.paymentAmount || 0), 0);

  const monthFee = revenues
    .filter(
      (r) => isCountableRevenue(r) && new Date(r.createdAt) >= startOfMonth,
    )
    .reduce(
      (sum, r) =>
        sum + Number(r.pgFeeAmount || 0) + Number(r.platformFeeAmount || 0),
      0,
    );

  // 목록이 periodEndAt 내림차순으로 오므로, 완료되지 않은 첫 항목이 곧 "진행 중인" 최신 정산
  const pendingSettlement =
    weeklySettlements.find((w) => w.status !== 'COMPLETED' && w.status !== 'FAILED') ||
    null;

  const cumulativeCompleted = weeklySettlements
    .filter((w) => w.status === 'COMPLETED')
    .reduce((sum, w) => sum + Number(w.payoutAmount || 0), 0);

  return { weekRevenue, monthFee, pendingSettlement, cumulativeCompleted };
}
