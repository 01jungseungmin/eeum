import {
  LayoutGrid,
  Store,
  Box,
  Layers,
  Tag,
  Ticket,
  ClipboardList,
  Calendar,
  Users,
  Heart,
  Star,
  MessageSquare,
  MessageCircle,
  Flag,
  TrendingUp,
  Bell,
  ShieldCheck,
  Settings,
  LogOut,
} from 'lucide-react';

const iconProps = { size: 20, strokeWidth: 1.5 };

export const MENU_CONFIG = [
  {
    group: '메뉴',
    items: [
      {
        id: 'dashboard',
        name: '대시보드',
        path: '/dashboard',
        icon: <LayoutGrid {...iconProps} />,
        subtitle: '오늘의 상점 현황을 한눈에 확인하세요',
      },
      {
        id: 'store',
        name: '상점 관리',
        path: '/store',
        icon: <Store {...iconProps} />,
        subtitle: '상점 정보를 수정하고 관리하세요',
      },
      {
        id: 'products',
        name: '상품 관리',
        path: '/products',
        icon: <Box {...iconProps} />,
        subtitle: '판매 중인 상품을 등록하고 관리하세요',
      },
      {
        id: 'categories',
        name: '카테고리 관리',
        path: '/categories',
        icon: <Layers {...iconProps} />,
        subtitle: '상품 카테고리를 설정하세요',
      },
      {
        id: 'events',
        name: '이벤트 관리',
        path: '/events',
        icon: <Tag {...iconProps} />,
        subtitle: '진행 중인 이벤트를 확인하세요',
      },
      {
        id: 'coupons',
        name: '쿠폰 관리',
        path: '/coupons',
        icon: <Ticket {...iconProps} />,
        subtitle: '할인 쿠폰을 발행하고 관리하세요',
      },
    ],
  },
  {
    group: '주문/고객',
    items: [
      {
        id: 'orders',
        name: '주문/예약 관리',
        path: '/1',
        icon: <ClipboardList {...iconProps} />,
        subtitle: '실시간 주문 및 예약 내역을 확인하세요',
        countKey: 'orders',
      },
      {
        id: 'calendar',
        name: '예약 캘린더',
        path: '/2',
        icon: <Calendar {...iconProps} />,
        subtitle: '일자별 예약 현황을 한눈에 보세요',
      },
      {
        id: 'customers',
        name: '고객 관리',
        path: '/3',
        icon: <Users {...iconProps} />,
        subtitle: '우리 가게 단골 손님을 관리하세요',
      },
      {
        id: 'wishlist',
        name: '관심 고객',
        path: '/4',
        icon: <Heart {...iconProps} />,
        subtitle: '상점을 찜한 고객 리스트입니다',
      },
      {
        id: 'reviews',
        name: '리뷰 관리',
        path: '/5',
        icon: <Star {...iconProps} />,
        subtitle: '고객들이 남긴 소중한 리뷰에 답글을 달아주세요',
        countKey: 'reviews',
      },
      {
        id: 'chat',
        name: '채팅',
        path: '/6',
        icon: <MessageSquare {...iconProps} />,
        subtitle: '고객과의 1:1 채팅을 관리하세요',
        countKey: 'chat',
      },
    ],
  },
  {
    group: '고객 지원',
    items: [
      {
        id: 'qna',
        name: '문의 관리',
        path: '/7',
        icon: <MessageCircle {...iconProps} />,
        subtitle: '매장 이용 관련 문의 사항에 답변하세요',
        countKey: 'qna',
      },
      {
        id: 'reports',
        name: '신고 내역',
        path: '/8',
        icon: <Flag {...iconProps} />,
        subtitle: '매장 신고 내역을 확인하세요',
        isSpecial: true,
      },
    ],
  },
  {
    group: '정산/알림',
    items: [
      {
        id: 'sales',
        name: '매출/정산',
        path: '/9',
        icon: <TrendingUp {...iconProps} />,
        subtitle: '이번 달 정산 예정 금액과 매출을 확인하세요',
      },
      {
        id: 'alerts',
        name: '알림',
        path: '/10',
        icon: <Bell {...iconProps} />,
        subtitle: '새로운 시스템 알림과 공지사항입니다',
        countKey: 'alerts',
      },
    ],
  },
  {
    group: '관리자',
    items: [
      {
        id: 'approval',
        name: '승인 상태',
        path: '/approval-status',
        icon: <ShieldCheck {...iconProps} />,
        subtitle: '사업자 등록증 인증 상태를 확인하세요',
        status: '확인필요',
      },
    ],
  },
];

// Helper: 경로로 데이터 하나만 찾아주는 함수
export const findMenuByPath = (path) => {
  for (const group of MENU_CONFIG) {
    const found = group.items.find((item) => item.path === path);
    if (found) return found;
  }
  return null;
};
