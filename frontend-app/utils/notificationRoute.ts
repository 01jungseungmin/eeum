// utils/notificationRoute.ts
// 알림 → 앱 화면 경로 변환 유틸
// HomeHeader와 알림센터(app/notification.tsx) 양쪽에서 공통으로 사용합니다.

import { NotificationItem } from '../api/notification';

/**
 * app/ 폴더에 실제로 존재하는 화면들 (파일 기준으로 정리됨)
 * 새 화면을 추가하면 여기에도 한 줄 추가하세요.
 */

// 파라미터 없는 고정 경로
const STATIC_ROUTES = [
  '/',                                  // app/index.tsx
  '/cart',                              // app/cart/index.tsx
  '/notification',                      // app/notification.tsx
  '/region-search',                     // app/region-search.tsx
  '/search',                            // app/search/index.tsx
  '/chat',                              // app/(tabs)/chat.tsx
  '/chat/create',                       // app/chat/create.tsx
  '/community',                         // app/(tabs)/community.tsx
  '/community/write',                   // app/community/write.tsx
  '/map',                               // app/(tabs)/map.tsx
  '/profile',                           // app/(tabs)/profile.tsx
  '/inquiry',                           // app/inquiry/index.tsx
  '/inquiry/write',                     // app/inquiry/write.tsx
  '/order/checkout',                    // app/order/checkout.tsx
  '/order/complete',                    // app/order/complete.tsx
  '/restaurant/reservation',            // app/restaurant/reservation.tsx
  '/restaurant/reservation-confirm',    // app/restaurant/reservation-confirm.tsx
  '/restaurant/reservation-detail',     // app/restaurant/reservation-detail.tsx
  '/restaurant/reservation-success',    // app/restaurant/reservation-success.tsx
  '/review/list',                       // app/review/list.tsx
  '/review/write',                      // app/review/write.tsx
  '/shop/list',                         // app/shop/list.tsx
  '/shop/event-list',                   // app/shop/event-list.tsx
  '/mypage/change-password',
  '/mypage/edit',
  '/mypage/favorites',
  '/mypage/history',
  '/mypage/my-community',
  '/mypage/my-reviews',
  '/mypage/notification-setting',
  '/mypage/profile-view',
  '/mypage/reservations',
  '/mypage/withdraw',
];

// [id] 동적 경로 (뒤에 숫자가 붙는 경로)
const DYNAMIC_ROUTE_PREFIXES = [
  '/chat/',        // app/chat/[id].tsx
  '/community/',   // app/community/[id].tsx
  '/inquiry/',     // app/inquiry/[id].tsx
  '/order/',       // app/order/[id].tsx
  '/product/',     // app/product/[id].tsx
  '/shop/',        // app/shop/[id].tsx
];

/** 백엔드가 준 linkUrl이 실제 존재하는 화면인지 검사 */
const isKnownRoute = (path: string) => {
  const pathOnly = path.split('?')[0];

  if (STATIC_ROUTES.includes(pathOnly)) return true;

  // /shop/12 처럼 프리픽스 + 값 형태인지 확인
  return DYNAMIC_ROUTE_PREFIXES.some((prefix) => {
    if (!pathOnly.startsWith(prefix)) return false;
    const rest = pathOnly.slice(prefix.length);
    return rest.length > 0 && !rest.includes('/');
  });
};

/**
 * refType(또는 type) + refId 조합으로 경로를 만듭니다.
 * 백엔드 enum 값에 맞춰 case를 추가/수정하세요.
 */
const buildRouteFromType = (item: any): string | null => {
  const type = (item?.refType || item?.type || '').toUpperCase();
  const refId = item?.refId ?? item?.referenceId ?? item?.targetId;

  switch (type) {
    // 주문 → app/order/[id].tsx
    case 'ORDER':
    case 'PAYMENT':
      return refId ? `/order/${refId}` : '/mypage/history';

    // 예약 → app/restaurant/reservation-detail.tsx ([id] 파일이 아니라 쿼리로 전달)
    case 'RESERVATION':
      return refId
        ? `/restaurant/reservation-detail?reservationId=${refId}`
        : '/mypage/reservations';

    // 리뷰 → refId가 storeId라고 가정. 내 리뷰 알림이면 '/mypage/my-reviews'로 바꾸세요
    case 'REVIEW':
      return refId ? `/review/list?storeId=${refId}` : '/mypage/my-reviews';

    // 채팅 → app/chat/[id].tsx
    case 'CHAT':
    case 'CHATROOM':
    case 'MESSAGE':
      return refId ? `/chat/${refId}` : '/chat';

    // 문의/신고 → app/inquiry/[id].tsx
    case 'INQUIRY':
    case 'QNA':
    case 'REPORT':
    case 'SYSTEM':
      return refId ? `/inquiry/${refId}` : '/inquiry';

    // 상점 → app/shop/[id].tsx
    case 'STORE':
    case 'SHOP':
      return refId ? `/shop/${refId}` : '/shop/list';

    // 상품 → app/product/[id].tsx
    case 'PRODUCT':
      return refId ? `/product/${refId}` : null;

    // 커뮤니티 게시글 → app/community/[id].tsx
    case 'COMMUNITY':
    case 'POST':
    case 'COMMENT':
      return refId ? `/community/${refId}` : '/community';

    // 찜 → app/mypage/favorites.tsx
    case 'FAVORITE':
      return '/mypage/favorites';

    default:
      return null;
  }
};

/**
 * 알림 하나를 받아서 이동할 경로를 반환합니다.
 * 이동할 곳이 없으면 null → 호출부에서 Alert 처리
 *
 * 우선순위: linkUrl(유효할 때) → refType 기반 매핑 → null
 */
export const getNotificationRoute = (item: NotificationItem | any): string | null => {
  const raw = item?.linkUrl ?? item?.link_url ?? item?.url ?? null;

  if (typeof raw === 'string' && raw.trim().length > 0) {
    let path = raw.trim();

    // 절대 URL(https://.../orders/1)이면 path만 추출
    if (path.startsWith('http://') || path.startsWith('https://')) {
      try {
        const parsed = new URL(path);
        path = parsed.pathname + parsed.search;
      } catch {
        path = '';
      }
    }

    if (path && !path.startsWith('/')) path = `/${path}`;

    // 백엔드 REST 경로 → 앱 화면 경로 보정
    path = path
      .replace(/^\/stores\//, '/shop/')
      .replace(/^\/stores$/, '/shop/list')
      .replace(/^\/orders\//, '/order/')
      .replace(/^\/orders$/, '/mypage/history')
      .replace(/^\/products\//, '/product/')
      .replace(/^\/chatrooms?\//, '/chat/')
      .replace(/^\/chats\//, '/chat/')
      .replace(/^\/inquiries\//, '/inquiry/')
      .replace(/^\/inquiries$/, '/inquiry')
      .replace(/^\/posts\//, '/community/')
      .replace(/^\/communities\//, '/community/')
      .replace(/^\/reviews\//, '/review/list?storeId=')
      .replace(/^\/reservations\/(\d+)$/, '/restaurant/reservation-detail?reservationId=$1')
      .replace(/^\/reservations$/, '/mypage/reservations')
      .replace(/^\/notifications$/, '/notification');

    if (path && isKnownRoute(path)) return path;

    // linkUrl은 있는데 앱에 없는 화면 → 타입 기반으로 재시도
    console.warn('⚠️ 앱에 없는 linkUrl:', raw, '→ refType으로 대체 시도');
  }

  return buildRouteFromType(item);
};