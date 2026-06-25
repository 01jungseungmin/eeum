# SDD Domain Index

| 도메인 | SDD 파일 | 코드 경로 | 비고 |
|---|---|---|---|
| account | ../docs/sdd/account.md | auth, account, admin/account, ownerInfo, accountRegion, region, location, audit | 회원, 인증, 활동지역, 사장승인, 감사로그 |
| store | ../docs/sdd/store.md | store, product, eventProduct, productCategory, productOption, storeReview, storeNotice, storeImage | 상점, 상품, 이벤트상품, 상점리뷰 포함 |
| order-payment | ../docs/sdd/order-payment.md | order, payment, cart, orderItem | 주문, 결제, 장바구니 |
| reservation | ../docs/sdd/reservation.md | reservation, visitReservation, reservationSetting, timeSlot | SDD 원문에서는 Order/Payment 안에 있으나 실제 구현 도메인 기준 분리 |
| used-product | ../docs/sdd/used-product.md | usedProduct, usedProductImage, usedProductReview | 중고거래 게시글, 거래 상태, 중고거래 리뷰 |
| community | ../docs/sdd/community.md | community, communityPost, communityComment, communityImage, postLike, commentLike | 게시글, 댓글, 대댓글, 좋아요 |
| chat | ../docs/sdd/chat.md | chat, chatRoom, chatParticipant, chatMessage, websocket | REST 채팅 + STOMP WebSocket |
| notification | ../docs/sdd/notification.md | notification, notificationSettings, push, fcm, sse | 알림, 알림 설정, 푸시 |
| favorite | ../docs/sdd/favorite.md | favorite | 상점/중고상품 찜 |
| inquiry | ../docs/sdd/inquiry.md | inquiry, inquiryReply, inquiryImage | 사용자/사장/관리자 문의 |
| report | ../docs/sdd/report.md | report | 신고 및 관리자 처리 |
| category | ../docs/sdd/category.md | category, adminCategory | 공통 카테고리 |
| image | ../docs/sdd/image.md | image, s3, imageBase, presignedUrl | 공통 이미지 처리 |
| common | ../docs/sdd/common.md | common, config, exception, security, aop, redis, event, scheduler | 공통 응답, 에러코드, 보안, 락, 이벤트, 페이징 |