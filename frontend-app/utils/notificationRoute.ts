import { NotificationItem } from '../api/notification';

// 파라미터 없는 고정 경로
const STATIC_ROUTES = [
  '/',                                  
  '/cart',                              
  '/notification',                      
  '/region-search',                     
  '/search',                            
  '/chat',                              
  '/chat/create',                       
  '/community',                         
  '/community/write',                   
  '/map',                               
  '/profile',                           
  '/inquiry',                           
  '/inquiry/write',                     
  '/order/checkout',                    
  '/order/complete',                    
  '/restaurant/reservation',            
  '/restaurant/reservation-confirm',    
  '/restaurant/reservation-detail',     
  '/restaurant/reservation-success',    
  '/review/list',                       
  '/review/write',                      
  '/shop/list',                         
  '/shop/event-list',                   
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
  '/community/',   // app/community/[id].tsx (사진 구조 반영)
  '/inquiry/',     // app/inquiry/[id].tsx
  '/order/',       // app/order/[id].tsx
  '/product/',     // app/product/[id].tsx
  '/shop/',        // app/shop/[id].tsx
];

/** 백엔드가 준 linkUrl이 실제 존재하는 화면인지 검사 */
const isKnownRoute = (path: string) => {
  const pathOnly = path.split('?')[0];

  if (STATIC_ROUTES.includes(pathOnly)) return true;

  return DYNAMIC_ROUTE_PREFIXES.some((prefix) => {
    if (!pathOnly.startsWith(prefix)) return false;
    const rest = pathOnly.slice(prefix.length);
    return rest.length > 0 && !rest.includes('/');
  });
};

const buildRouteFromType = (item: any): string | null => {
  const type = (item?.refType || item?.type || '').toUpperCase();
  const refId = item?.refId ?? item?.referenceId ?? item?.targetId;

  switch (type) {
    case 'ORDER':
    case 'PAYMENT':
      return refId ? `/order/${refId}` : '/mypage/history';

    // 1. 예약 알림 하얀 화면 방지 (파라미터를 id로 통일)
    case 'RESERVATION':
    case 'RESERVATION_CONFIRMED':
      return refId
        ? `/restaurant/reservation-detail?id=${refId}`
        : '/mypage/reservations';

    case 'REVIEW':
      return '/mypage/my-reviews';

    // 💡 2. 커뮤니티 알림 연결
    case 'COMMUNITY':
    case 'POST':
    case 'COMMENT':
      return refId ? `/community/${refId}` : '/mypage/my-community';

    // 💡 3. 상점 문의 & 내 문의 답변 모두 동일하게 연결
    case 'INQUIRY':
    case 'INQUIRY_ANSWER':
    case 'STORE_INQUIRY':
    case 'QNA':
    case 'REPORT':
    case 'SYSTEM':
      return refId ? `/inquiry/${refId}` : '/inquiry';

    case 'CHAT':
    case 'CHATROOM':
    case 'MESSAGE':
      return refId ? `/chat/${refId}` : '/chat';

    case 'STORE':
    case 'SHOP':
      return refId ? `/shop/${refId}` : '/shop/list';

    case 'PRODUCT':
      return refId ? `/product/${refId}` : null;

    case 'FAVORITE':
      return '/mypage/favorites';

    default:
      return null;
  }
};

export const getNotificationRoute = (item: NotificationItem | any): string | null => {
  const raw = item?.linkUrl ?? item?.link_url ?? item?.url ?? null;

  if (typeof raw === 'string' && raw.trim().length > 0) {
    let path = raw.trim();

    if (path.startsWith('http://') || path.startsWith('https://')) {
      try {
        const parsed = new URL(path);
        path = parsed.pathname + parsed.search;
      } catch {
        path = '';
      }
    }

    if (path && !path.startsWith('/')) path = `/${path}`;

    // 백엔드 REST 경로 → 프론트엔드 앱 화면 경로 정규식 맵핑
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
      .replace(/^\/community\/posts\//, '/community/') 
      .replace(/^\/posts\//, '/community/')
      .replace(/^\/communities\//, '/community/') 
      .replace(/^\/communities$/, '/mypage/my-community')
      .replace(/^\/reviews.*/, '/mypage/my-reviews')
      .replace(/^\/reservations\/(\d+)$/, '/restaurant/reservation-detail?id=$1')
      .replace(/^\/reservations$/, '/mypage/reservations')
      .replace(/^\/notifications$/, '/notification');

    if (path && isKnownRoute(path)) return path;

    console.warn('⚠️ 앱에 없는 linkUrl:', raw, '→ refType으로 대체 시도');
  }

  return buildRouteFromType(item);
};