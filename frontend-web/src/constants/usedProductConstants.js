// 관리자 중고거래 관리 도메인 상수 (백엔드 enum과 1:1 매칭)
// domain/used/enums/UsedProductStatus.java
export const USED_PRODUCT_STATUS_LABEL = {
  SELLING: '판매중',
  RESERVED: '예약중',
  SOLD: '판매완료',
};

// domain/used/enums/UsedProductPriceType.java
export const USED_PRODUCT_PRICE_TYPE_LABEL = {
  FIXED: '정가',
  FREE: '나눔',
  NEGOTIABLE: '가격 제안',
};

export const formatUsedProductPrice = (priceType, price) => {
  if (priceType === 'FREE') return '나눔';
  if (priceType === 'NEGOTIABLE') return '가격 제안';
  return `${Number(price || 0).toLocaleString()}원`;
};
