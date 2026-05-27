// src/constants/shopDummyData.ts

// 1. 카테고리 데이터 (category 테이블)
// 전체를 뜻하는 'all' 카테고리(id: 0)를 프론트엔드용으로 하나 추가해 둡니다.
export const SHOP_CATEGORIES = [
  { id: 0, name: '전체' },
  { id: 1, name: '음식점' },
  { id: 2, name: '카페/디저트' },
  { id: 3, name: '반찬/도시락' },
  { id: 4, name: '정육/수산' },
  { id: 5, name: '베이커리' },
  { id: 6, name: '편의점/마트' },
  { id: 7, name: '기타' },
];

// 2. 상점 상세 데이터 (store, store_notice, product_category, product, product_image, event_product 통합)
export const DUMMY_SHOPS = [
  {
    id: 1,
    categoryId: 1,
    name: '맛있는 반찬가게',
    status: 'OPEN',
    address: '서울특별시 종로구 청운동 123-4',
    phone: '02-1234-5678',
    description: '매일 신선한 반찬을 만들어요!',
    businessHours: '월~토 09:00~19:00',
    rating: 4.5,
    favoriteCount: 10,
    reviewCount: 5,
    notices: [
      { id: 1, title: '설 연휴 휴무 안내', content: '설 연휴 기간(1/28~1/30) 동안 휴무합니다.', type: 'CLOSED_TODAY' },
      { id: 2, title: '신메뉴 출시 안내', content: '봄 신메뉴 나물반찬 세트가 출시되었습니다.', type: 'NORMAL' }
    ],
    productCategories: [
      { id: 1, name: '반찬류' },
      { id: 2, name: '국/찌개' },
      { id: 3, name: '밑반찬' }
    ],
    products: [
      { id: 1, categoryId: 1, name: '김치찌개', description: '직접 담근 김치로 끓인 찌개입니다.', price: 8000, type: 'MENU', imageUrl: 'https://via.placeholder.com/400x300?text=김치찌개', status: 'ACTIVE' },
      { id: 2, categoryId: 1, name: '된장찌개', description: '구수한 된장찌개입니다.', price: 7000, type: 'MENU', imageUrl: 'https://via.placeholder.com/400x300?text=된장찌개', status: 'ACTIVE' },
      // event_product 테이블에 있는 할인가(3500원) 반영
      { id: 3, categoryId: 2, name: '깍두기 500g', description: '아삭아삭 깍두기입니다.', price: 5000, eventPrice: 3500, stock: 50, type: 'SALE', imageUrl: 'https://via.placeholder.com/400x300?text=깍두기', status: 'ACTIVE' },
      { id: 4, categoryId: 2, name: '배추김치 1kg', description: '매콤한 배추김치입니다.', price: 12000, stock: 30, type: 'SALE', imageUrl: 'https://via.placeholder.com/400x300?text=배추김치', status: 'ACTIVE' },
      // 예약 전용 상품 (type: RESERVATION)
      { id: 5, categoryId: 3, name: '도시락 예약', description: '점심 도시락 예약 상품입니다.', price: 8500, stock: 20, type: 'RESERVATION', imageUrl: 'https://via.placeholder.com/400x300?text=도시락예약', status: 'ACTIVE' }
    ]
  },
  {
    id: 2,
    categoryId: 2,
    name: '청운 카페',
    status: 'TEMP_CLOSED', // 현재 임시 휴업(오픈 준비 중) 상태
    address: '서울특별시 종로구 신교동 456-7',
    phone: '02-2345-6789',
    description: '편안한 카페입니다.',
    businessHours: '월~일 10:00~21:00',
    rating: 0.0,
    favoriteCount: 0,
    reviewCount: 0,
    notices: [
      { id: 4, title: '오픈 준비 중', content: '곧 오픈할 예정입니다. 많은 기대 부탁드립니다.', type: 'NORMAL' }
    ],
    productCategories: [
      { id: 4, name: '커피' },
      { id: 5, name: '논커피' },
      { id: 6, name: '디저트' }
    ],
    products: [
      { id: 6, categoryId: 4, name: '아메리카노', description: '진한 에스프레소 아메리카노입니다.', price: 4500, type: 'MENU', imageUrl: 'https://via.placeholder.com/400x300?text=아메리카노', status: 'ACTIVE' },
      { id: 7, categoryId: 4, name: '카페라떼', description: '부드러운 우유와 에스프레소.', price: 5000, type: 'MENU', imageUrl: 'https://via.placeholder.com/400x300?text=카페라떼', status: 'ACTIVE' },
      { id: 8, categoryId: 5, name: '초코스무디', description: '달콤한 초코 스무디입니다.', price: 5500, type: 'MENU', imageUrl: 'https://via.placeholder.com/400x300?text=초코스무디', status: 'ACTIVE' },
      // event_product 테이블에 있는 예정 할인가(2800원) 반영
      { id: 9, categoryId: 6, name: '크로플', description: '바삭한 크로플입니다.', price: 4000, eventPrice: 2800, stock: 30, type: 'SALE', imageUrl: 'https://via.placeholder.com/400x300?text=크로플', status: 'ACTIVE' },
      // 품절 상태 반영
      { id: 10, categoryId: 6, name: '티라미수', description: '부드러운 티라미수 케이크입니다.', price: 6000, stock: 15, type: 'SALE', imageUrl: 'https://via.placeholder.com/400x300?text=티라미수', status: 'SOLD_OUT' }
    ]
  },
  {
    id: 3,
    categoryId: 3,
    name: '궁정 도시락',
    status: 'TEMP_CLOSED', // 승인 거절된 식당이라 닫혀있음
    address: '서울특별시 종로구 궁정동 789-1',
    phone: '02-3456-7890',
    description: '건강한 도시락을 드립니다.',
    businessHours: '',
    rating: 0.0,
    favoriteCount: 0,
    reviewCount: 0,
    notices: [],
    productCategories: [],
    products: []
  }
];