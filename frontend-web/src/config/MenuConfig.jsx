import {
  LayoutGrid,
  Store,
  Box,
  Layers,
  Tag,
  ClipboardList,
  Calendar,
  Users,
  Star,
  MessageSquare,
  MessageCircle,
  Flag,
  TrendingUp,
  Bell,
  ShieldCheck,
  FileText,
  FolderTree,
  UserCheck,
  Settings,
  Sparkles,
  MessageSquareText,
  HeartHandshake,
  LogOut,
} from 'lucide-react';

const iconProps = { size: 20, strokeWidth: 1.5 };

export const OWNER_MENU_CONFIG = [
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
        subtitle: '특가 및 시간 제한 이벤트 상품을 관리하세요',
      },
    ],
  },
  {
    group: 'AI 매니저',
    items: [
      {
        id: 'ai-manager',
        name: 'AI 매니저',
        path: '/ai-manager',
        icon: <Sparkles {...iconProps} />,
        subtitle: 'AI가 정리한 오늘의 할 일을 확인하세요',
      },
      {
        id: 'ai-customer-care',
        name: 'AI 고객 케어',
        path: '/ai-manager/customer-care',
        icon: <HeartHandshake {...iconProps} />,
        subtitle: '지금 말을 걸어야 할 고객을 확인하세요',
      },
      {
        id: 'ai-messages',
        name: 'AI 생성 메시지',
        path: '/ai-manager/messages',
        icon: <MessageSquareText {...iconProps} />,
        subtitle: 'AI가 만든 초안을 검토하고 발송하세요',
      },
    ],
  },
  {
    group: '주문/고객',
    items: [
      {
        id: 'orders',
        name: '주문/예약 관리',
        path: '/order-management',
        icon: <ClipboardList {...iconProps} />,
        subtitle: '실시간 주문 및 예약 내역을 확인하세요',
        countKey: 'orders',
      },
      {
        id: 'calendar',
        name: '예약 캘린더',
        path: '/reservation',
        icon: <Calendar {...iconProps} />,
        subtitle: '일자별 예약 현황을 한눈에 보세요',
      },
      {
        id: 'customers',
        name: '고객 관리',
        path: '/customers',
        icon: <Users {...iconProps} />,
        subtitle: '우리 가게 단골 손님을 관리하세요',
      },
      {
        id: 'reviews',
        name: '리뷰 관리',
        path: '/reviews',
        icon: <Star {...iconProps} />,
        subtitle: '고객들이 남긴 소중한 리뷰에 답글을 달아주세요',
        countKey: 'reviews',
      },

      {
        id: 'chat',
        name: '채팅',
        path: '/chat',
        icon: <MessageSquare {...iconProps} />,
        subtitle: '고객과 실시간으로 소통하세요',
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
        path: '/inquiry',
        icon: <MessageCircle {...iconProps} />,
        subtitle: '매장 이용 관련 문의 사항에 답변하세요',
        countKey: 'qna',
      },
      {
        id: 'reports',
        name: '신고 내역',
        path: '/reports',
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
        path: '/notifications',
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
  {
    group: '설정',
    items: [
      {
        id: 'logout',
        name: '로그아웃',
        path: '#',
        action: 'LOGOUT',
        icon: <LogOut {...iconProps} />,
      },
    ],
  },
];

export const ADMIN_MENU_CONFIG = [
  {
    group: '',
    items: [
      {
        id: 'admin-dashboard',
        name: '대시보드',
        path: '/admin/dashboard',
        icon: <LayoutGrid {...iconProps} />,
      },
      {
        id: 'admin-members',
        name: '회원 관리',
        path: '/admin/members',
        icon: <Users {...iconProps} />,
      },
      {
        id: 'admin-approval',
        name: '사장 승인',
        path: '/admin/approval',
        icon: <UserCheck {...iconProps} />,
        countKey: 'adminApproval',
      },
      {
        id: 'admin-posts',
        name: '게시글',
        path: '/admin/posts',
        icon: <FileText {...iconProps} />,
      },
      {
        id: 'admin-reports',
        name: '신고',
        path: '/admin/reports',
        icon: <Flag {...iconProps} />,
        countKey: 'adminReports',
      },
      {
        id: 'admin-inquiry',
        name: '문의',
        path: '/admin/inquiry',
        icon: <MessageCircle {...iconProps} />,
      },
      {
        id: 'admin-categories',
        name: '카테고리',
        path: '/admin/categories',
        icon: <FolderTree {...iconProps} />,
      },
      {
        id: 'admin-logs',
        name: '관리자 로그',
        path: '/admin/logs',
        icon: <ClipboardList {...iconProps} />,
      },
    ],
  },
  {
    group: '설정',
    items: [
      {
        id: 'admin-settings',
        name: '설정',
        path: '/admin/settings',
        icon: <Settings {...iconProps} />,
      },
      {
        id: 'admin-logout',
        name: '로그아웃',
        path: '#',
        action: 'LOGOUT',
        icon: <LogOut {...iconProps} />,
      },
    ],
  },
];

// 브레드크럼이나 헤더 타이틀 매칭 함수 리팩토링
export const findMenuByPath = (path, role) => {
  const targetConfig =
    role === 'ROLE_ADMIN' ? ADMIN_MENU_CONFIG : OWNER_MENU_CONFIG;

  if (!Array.isArray(targetConfig)) return null;

  for (const group of targetConfig) {
    const found = group.items.find((item) => item.path === path);
    if (found) return found;
  }
  return null;
};
