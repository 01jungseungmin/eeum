import { ShoppingCart, Heart, MessageSquare } from 'lucide-react';

// careType별 설정 상수
export const CARE_TYPE_CONFIG = {
  CART_INTEREST: {
    icon: ShoppingCart,
    iconBgColor: '#e0f2fe',
    iconColor: '#0284c7',
    defaultTitle: '구매 관심이 높은 고객',
    defaultDesc: '장바구니에 상품을 담았지만 아직 주문하지 않은 고객',
  },
  INACTIVE_REGULAR: {
    icon: Heart,
    iconBgColor: '#fce7f3',
    iconColor: '#db2777',
    defaultTitle: '한동안 방문이 없는 단골',
    defaultDesc: '최근 방문 또는 주문 이력이 없는 기존 단골 고객',
  },
  INQUIRY_HESITATION: {
    icon: MessageSquare,
    iconBgColor: '#f3e8ff',
    iconColor: '#9333ea',
    defaultTitle: '문의 후 망설이는 고객',
    defaultDesc: '문의까지 했지만 주문으로 이어지지 않은 고객',
    secondaryAction: true,
  },
};
