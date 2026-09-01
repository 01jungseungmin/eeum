package com.eeum.eeum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode { // API에서 발생 가능한 에러 코드 정의

    /*
      ===================== 에러 코드 구조 =====================
      ENUM_상수명("에러 코드 문자열", "에러 메시지", HTTP 상태코드)

      예시:
      COMMON_INVALID_PARAMETER("COMMON_001", "잘못된 요청 파라미터입니다", HttpStatus.BAD_REQUEST)
     */

    // ===================== 공통 (COMMON) =====================
    COMMON_INVALID_PARAMETER("COMMON_001", "잘못된 요청 파라미터입니다", HttpStatus.BAD_REQUEST),
    COMMON_RESOURCE_NOT_FOUND("COMMON_002", "요청한 리소스를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    COMMON_INTERNAL_ERROR("COMMON_003", "서버 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    COMMON_UNAUTHORIZED("COMMON_004", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    COMMON_FORBIDDEN("COMMON_005", "접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    COMMON_DUPLICATE_REQUEST("COMMON_006", "중복된 요청입니다", HttpStatus.CONFLICT),
    COMMON_CONCURRENT_ACCESS("COMMON_007", "동시 접근 오류입니다. 잠시 후 다시 시도해 주세요", HttpStatus.CONFLICT),
    COMMON_DUPLICATE_RESOURCE("COMMON_008","이미 존재하는 리소스입니다.",HttpStatus.CONFLICT ),
    COMMON_NOT_FOUND("COMMON_009","존재하지 않는 리소스입니다.",HttpStatus.NOT_FOUND),
    COMMON_CONFLICT("COMMON_010", "요청 처리 중 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    COMMON_METHOD_NOT_ALLOWED("COMMON_011", "지원하지 않는 HTTP 메서드입니다", HttpStatus.METHOD_NOT_ALLOWED),

    // ===================== 인증 (AUTH) =====================
    AUTH_INVALID_TOKEN("AUTH_001", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_EXPIRED_TOKEN("AUTH_002", "만료된 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_PASSWORD("AUTH_003", "비밀번호가 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_004", "이메일 인증이 완료되지 않았습니다", HttpStatus.BAD_REQUEST),
    AUTH_INVALID_VERIFICATION_CODE("AUTH_005", "유효하지 않은 인증 코드입니다", HttpStatus.BAD_REQUEST),
    AUTH_EXPIRED_VERIFICATION_CODE("AUTH_006", "만료된 인증 코드입니다", HttpStatus.BAD_REQUEST),
    AUTH_REAUTH_REQUIRED("AUTH_007", "재인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_REAUTH_TOKEN("AUTH_008", "유효하지 않은 재인증 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_RESET_TOKEN("AUTH_009", "유효하지 않은 비밀번호 재설정 토큰입니다", HttpStatus.BAD_REQUEST),
    AUTH_RATE_LIMITED("AUTH_010", "요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요", HttpStatus.TOO_MANY_REQUESTS),
    AUTH_OAUTH_FAILED("AUTH_011", "소셜 로그인 처리 중 오류가 발생했습니다", HttpStatus.BAD_REQUEST),

    // ===================== 회원 (ACCOUNT) =====================
    ACCOUNT_NOT_FOUND("ACCOUNT_001", "존재하지 않는 회원입니다", HttpStatus.NOT_FOUND),
    ACCOUNT_DUPLICATE_EMAIL("ACCOUNT_002", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    ACCOUNT_DUPLICATE_NICKNAME("ACCOUNT_003", "이미 사용 중인 닉네임입니다", HttpStatus.CONFLICT),
    ACCOUNT_SUSPENDED("ACCOUNT_004", "정지된 계정입니다", HttpStatus.FORBIDDEN),
    ACCOUNT_WITHDRAWN("ACCOUNT_005", "탈퇴한 계정입니다", HttpStatus.FORBIDDEN),
    ACCOUNT_INVALID_PASSWORD_FORMAT("ACCOUNT_006", "비밀번호는 영문, 숫자, 특수문자를 포함하여 8자 이상이어야 합니다", HttpStatus.BAD_REQUEST),
    ACCOUNT_OWNER_ALREADY_EXISTS("ACCOUNT_007", "이미 사업자 정보가 등록되어 있습니다", HttpStatus.CONFLICT),
    ACCOUNT_OWNER_NOT_FOUND("ACCOUNT_008", "사업자 정보를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    ACCOUNT_OWNER_NOT_APPROVED("ACCOUNT_009", "사장 회원 승인이 완료되지 않았습니다", HttpStatus.FORBIDDEN),
    ACCOUNT_DUPLICATE_BUSINESS_NUMBER("ACCOUNT_010", "이미 등록된 사업자번호입니다", HttpStatus.CONFLICT),
    ACCOUNT_INVALID_BUSINESS_NUMBER("ACCOUNT_011", "유효하지 않은 사업자번호입니다", HttpStatus.BAD_REQUEST),
    ACCOUNT_ALREADY_EXISTS("ACCOUNT_012","이미 등록된 회원입니다",HttpStatus.CONFLICT),
    ACCOUNT_PRIMARY_REGION_NOT_FOUND("ACCOUNT_013","대표 지역이 없습니다.",HttpStatus.NOT_FOUND),
    ACCOUNT_ALREADY_SUSPENDED("ACCOUNT_014", "이미 정지된 회원입니다", HttpStatus.CONFLICT),
    ACCOUNT_NOT_SUSPENDED("ACCOUNT_015", "정지 상태가 아닌 회원입니다", HttpStatus.CONFLICT),
    ACCOUNT_SIGNUP_INCOMPLETE("ACCOUNT_016", "회원가입이 완료되지 않은 계정입니다", HttpStatus.FORBIDDEN),
    ACCOUNT_ALREADY_ANONYMIZED("ACCOUNT_017", "개인정보가 파기된 계정은 복구할 수 없습니다", HttpStatus.CONFLICT),
    ACCOUNT_ADMIN_SANCTION_NOT_ALLOWED("ACCOUNT_018", "관리자 계정에는 제재를 적용할 수 없습니다", HttpStatus.FORBIDDEN),

    // ===================== 사장 추가정보 기입 =====================
    OWNER_ALREADY_APPROVED( "OWNER_001","이미 승인된 사장 계정입니다.",HttpStatus.CONFLICT),
    OWNER_REVIEW_ALREADY_REQUESTED("OWNER_002","이미 입점 심사 요청이 접수되었습니다.",HttpStatus.CONFLICT),
    OWNER_CHECKLIST_NOT_COMPLETED( "OWNER_003","입점 심사 필수 항목을 모두 완료해야 합니다.",HttpStatus.BAD_REQUEST),
    OWNER_INFO_NOT_FOUND( "OWNER_004","사장 신청 정보를 찾을 수 없습니다.",HttpStatus.NOT_FOUND),
    OWNER_REVIEW_NOT_PENDING("OWNER_005","심사 대기 중인 신청이 아닙니다.",HttpStatus.CONFLICT),

    // ===================== 사업자 인증 (BUSINESST) =====================
    BUSINESS_VERIFY_FAILED("BUSINESS_001", "사업자등록정보 검증에 실패했습니다.", HttpStatus.BAD_REQUEST),
    BUSINESS_API_FAILED("BUSINESS_002", "사업자등록정보 API 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    BUSINESS_INVALID_OPENING_DATE("BUSINESS_003", "개업일자 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

    // ===================== 활동 지역 (REGION) =====================
    REGION_NOT_FOUND("REGION_001", "존재하지 않는 지역입니다", HttpStatus.NOT_FOUND),
    REGION_ALREADY_REGISTERED("REGION_002", "이미 등록된 활동 지역입니다", HttpStatus.CONFLICT),
    REGION_MAX_LIMIT_EXCEEDED("REGION_003", "활동 지역은 최대 2개까지 등록할 수 있습니다", HttpStatus.BAD_REQUEST),
    REGION_NOT_VERIFIED("REGION_004", "GPS 인증이 완료되지 않은 지역입니다", HttpStatus.FORBIDDEN),
    REGION_GPS_MISMATCH("REGION_005", "현재 위치가 등록된 지역과 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    REGION_ACCESS_REQUIRED("REGION_006", "활동 지역 인증이 필요합니다", HttpStatus.FORBIDDEN),
    REGION_LOCATION_NOT_FOUND("REGION_007","지역 위치 정보를 찾아올 수 없습니다.",HttpStatus.NOT_FOUND),

    // ===================== 상점 (STORE) =====================
    STORE_NOT_FOUND("STORE_001", "존재하지 않는 상점입니다", HttpStatus.NOT_FOUND),
    STORE_ALREADY_EXISTS("STORE_002", "이미 상점이 등록되어 있습니다", HttpStatus.CONFLICT),
    STORE_ACCESS_DENIED("STORE_003", "상점 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    STORE_SUSPENDED("STORE_004", "정지된 상점입니다", HttpStatus.FORBIDDEN),
    STORE_CLOSED("STORE_005", "영업 중인 상점이 아닙니다", HttpStatus.BAD_REQUEST),
    STORE_CATEGORY_REQUIRED( "STORE_006", "상점 업종을 선택해야 합니다.",HttpStatus.BAD_REQUEST),
    STORE_NOTICE_NOT_FOUND("STORE_007", "공지를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // ===================== 상품 (PRODUCT) =====================
    PRODUCT_NOT_FOUND("PRODUCT_001", "존재하지 않는 상품입니다", HttpStatus.NOT_FOUND),
    PRODUCT_OUT_OF_STOCK("PRODUCT_002", "재고가 부족합니다", HttpStatus.BAD_REQUEST),
    PRODUCT_INACTIVE("PRODUCT_003", "판매 중인 상품이 아닙니다", HttpStatus.BAD_REQUEST),
    PRODUCT_CATEGORY_NOT_FOUND("PRODUCT_004", "상품 카테고리를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_CATEGORY_HAS_PRODUCTS("CATEGORY_005", "소속 상품이 있어 삭제할 수 없습니다", HttpStatus.BAD_REQUEST),
    PRODUCT_OPTION_NOT_FOUND("PRODUCT_006", "상품 옵션을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_OPTION_ITEM_NOT_FOUND("OPTION_007", "옵션 항목을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PRODUCT_REQUIRED_OPTION_MISSING("PRODUCT_008", "필수 옵션을 선택해 주세요", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_PURCHASABLE("PRODUCT_009", "구매할 수 없는 상품입니다.", HttpStatus.BAD_REQUEST),
    PRODUCT_IMAGE_NOT_FOUND("PRODUCT_010", "상품 이미지를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // ===================== 이벤트 상품 (EVENT) =====================
    EVENT_NOT_FOUND("EVENT_001", "존재하지 않는 이벤트 상품입니다", HttpStatus.NOT_FOUND),
    EVENT_NOT_ACTIVE("EVENT_002", "진행 중인 이벤트가 아닙니다", HttpStatus.BAD_REQUEST),
    EVENT_OUT_OF_STOCK("EVENT_003", "이벤트 재고가 부족합니다", HttpStatus.BAD_REQUEST),
    EVENT_ALREADY_ACTIVE("EVENT_004", "이미 활성화된 이벤트가 존재합니다", HttpStatus.CONFLICT),
    EVENT_PRODUCT_INVALID_PERIOD( "EVENT_005", "이벤트 시작 시간은 종료 시간보다 빨라야 합니다.",HttpStatus.BAD_REQUEST),
    EVENT_PRODUCT_INVALID_PRICE( "EVENT_006", "이벤트 가격은 원래 가격보다 낮아야 합니다.",HttpStatus.BAD_REQUEST),

    // ===================== 장바구니 (CART) =====================
    CART_DIFFERENT_STORE("CART_001", "동일한 상점의 상품만 담을 수 있습니다", HttpStatus.BAD_REQUEST),
    CART_ITEM_NOT_FOUND("CART_002", "장바구니 상품을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    CART_EMPTY("CART_003", "장바구니가 비어있습니다", HttpStatus.BAD_REQUEST),
    CART_ACCESS_DENIED("CART_004","장바구니 접근 권한이 없습니다.",HttpStatus.FORBIDDEN),
    CART_NOT_FOUND("CART_005","장바구니를 찾을 수 없습니다.",HttpStatus.NOT_FOUND),

    // ===================== 주문 (ORDER) =====================
    ORDER_NOT_FOUND("ORDER_001", "존재하지 않는 주문입니다", HttpStatus.NOT_FOUND),
    ORDER_CANCEL_NOT_ALLOWED("ORDER_002", "취소할 수 없는 주문 상태입니다", HttpStatus.BAD_REQUEST),
    ORDER_ACCESS_DENIED("ORDER_003", "주문 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    ORDER_ALREADY_PAID("ORDER_004", "이미 결제된 주문입니다", HttpStatus.CONFLICT),
    ORDER_EXPIRED("ORDER_005", "만료된 주문입니다", HttpStatus.BAD_REQUEST),
    ORDER_TYPE_MISMATCH("ORDER_006", "서로 다른 주문 유형의 상품은 함께 주문할 수 없습니다", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_STATUS("ORDER_007", "유효하지 않은 주문 상태 입니다.", HttpStatus.BAD_REQUEST),
    ORDER_REFUND_NOT_ALLOWED("ORDER_008", "환불 요청할 수 없는 주문 상태입니다", HttpStatus.BAD_REQUEST),

    // ===================== 결제 (PAYMENT) =====================
    PAYMENT_NOT_FOUND("PAYMENT_001", "존재하지 않는 결제 정보입니다", HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_MISMATCH("PAYMENT_002", "결제 금액이 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    PAYMENT_CANCEL_NOT_ALLOWED("PAYMENT_003", "취소할 수 없는 결제 상태입니다", HttpStatus.BAD_REQUEST),
    PAYMENT_DUPLICATE("PAYMENT_004", "이미 처리된 결제입니다", HttpStatus.CONFLICT),
    PAYMENT_WEBHOOK_INVALID("PAYMENT_005", "유효하지 않은 Webhook 요청입니다", HttpStatus.UNAUTHORIZED),
    PAYMENT_INVALID_STATUS("PAYMENT_006", "유효하지 않은 결제 상태입니다", HttpStatus.BAD_REQUEST),
    PAYMENT_REFUND_FAILED("PAYMENT_007", "환불 처리에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    PAYMENT_VERIFY_FAILED("PAYMENT_008", "결제 검증에 실패했습니다", HttpStatus.BAD_REQUEST),
    PAYMENT_METHOD_NOT_SUPPORTED("PAYMENT_009", "지원하지 않는 결제 수단입니다", HttpStatus.BAD_REQUEST),
    PAYMENT_REFUND_ALREADY("PAYMENT_010", "이미 환불 처리 되었습니다.", HttpStatus.CONFLICT),
    PAYMENT_NOT_COMPLETED("PAYMENT_011", "결제가 완료되지 않았습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_REFUND_NOT_REQUESTED("PAYMENT_012", "환불 요청 상태가 아닙니다.", HttpStatus.BAD_REQUEST),
    // 서명 검증 이전 단계에서 걸러지는 형식 오류 — 인증 실패(401)와 구분해 400으로 응답한다
    PAYMENT_WEBHOOK_MALFORMED("PAYMENT_013", "형식이 올바르지 않은 Webhook 요청입니다", HttpStatus.BAD_REQUEST),

    // ===================== 예약 (RESERVATION) =====================
    RESERVATION_NOT_FOUND("RESERVATION_001", "존재하지 않는 예약입니다", HttpStatus.NOT_FOUND),
    RESERVATION_CANCEL_NOT_ALLOWED("RESERVATION_003", "취소할 수 없는 예약 상태입니다", HttpStatus.BAD_REQUEST),
    RESERVATION_ACCESS_DENIED("RESERVATION_004", "예약 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    RESERVATION_TIME_UNAVAILABLE("RESERVATION_005", "해당 시간대는 예약할 수 없습니다", HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_NOT_RESERVABLE("RESERVATION_006","현재 예약할 수 없는 상점입니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_CLOSED_DAY("RESERVATION_007", "해당 요일은 상점 휴무일입니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_OUTSIDE_BUSINESS_HOURS("RESERVATION_008", "상점 영업시간 외에는 예약할 수 없습니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_BUSINESS_HOURS_NOT_SET("RESERVATION_009", "상점 영업시간이 등록되어 있지 않습니다.",HttpStatus.BAD_REQUEST),
    VISIT_RESERVATION_ALREADY_EXISTS("RESERVATION_010","해당 시간에는 이미 방문 예약이 있습니다.",HttpStatus.CONFLICT),
    RESERVATION_SETTING_NOT_FOUND("RESERVATION_012","예약 설정을 찾을 수 없습니다.",HttpStatus.NOT_FOUND),
    RESERVATION_DISABLED("RESERVATION_013","해당 상점은 방문 예약 기능을 사용하지 않습니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_TABLE_UNAVAILABLE("RESERVATION_014", "해당 시간에 예약 가능한 테이블이 없습니다.", HttpStatus.CONFLICT),
    RESERVATION_INVALID_PARTY_SIZE("RESERVATION_015", "예약 인원 수가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    RESERVATION_INVALID_SLOT_TIME("RESERVATION_016", "예약 가능한 시간 단위가 아닙니다.", HttpStatus.BAD_REQUEST),
    RESERVATION_TABLE_CHANGE_NOT_ALLOWED("RESERVATION_018", "진행 중인 미래 예약이 있어 테이블 구성을 변경할 수 없습니다.", HttpStatus.CONFLICT),
    RESERVATION_APPROVE_NOT_ALLOWED("RESERVATION_019", "승인할 수 없는 예약 상태입니다.", HttpStatus.BAD_REQUEST),
    RESERVATION_REJECT_NOT_ALLOWED("RESERVATION_020", "거절할 수 없는 예약 상태입니다.", HttpStatus.BAD_REQUEST),
    RESERVATION_COMPLETE_NOT_ALLOWED("RESERVATION_021", "완료 처리할 수 없는 예약 상태입니다.", HttpStatus.BAD_REQUEST),
    RESERVATION_SLOT_CHANGE_NOT_ALLOWED("RESERVATION_022", "활성 예약이 있는 시간대는 비활성화할 수 없습니다.", HttpStatus.CONFLICT),

    // ===================== 중고거래 (USED) =====================
    USED_PRODUCT_NOT_FOUND("USED_001", "존재하지 않는 중고 게시글입니다", HttpStatus.NOT_FOUND),
    USED_PRODUCT_ACCESS_DENIED("USED_002", "중고 게시글 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    USED_PRODUCT_NOT_ON_SALE("USED_003", "판매 중인 상품이 아닙니다", HttpStatus.BAD_REQUEST),
    USED_PRODUCT_ALREADY_RESERVED("USED_004", "이미 예약된 상품입니다", HttpStatus.CONFLICT),
    USED_PRODUCT_ALREADY_SOLD("USED_005", "이미 판매된 상품입니다", HttpStatus.BAD_REQUEST),
    USED_REVIEW_NOT_FOUND("USED_006", "존재하지 않는 중고거래 리뷰입니다", HttpStatus.NOT_FOUND),
    USED_REVIEW_ALREADY_EXISTS("USED_007", "이미 리뷰를 작성했습니다", HttpStatus.CONFLICT),
    USED_REVIEW_NOT_COMPLETED("USED_008", "거래가 완료된 후 리뷰를 작성할 수 있습니다", HttpStatus.BAD_REQUEST),
    USED_PRODUCT_INVALID_CATEGORY("USED_009", "중고거래 카테고리가 아닙니다", HttpStatus.BAD_REQUEST),
    USED_PRODUCT_INVALID_PRICE("USED_010", "거래 유형과 가격이 맞지 않습니다", HttpStatus.BAD_REQUEST),
    USED_PRODUCT_DELETE_NOT_ALLOWED("USED_011", "예약 중인 게시글은 삭제할 수 없습니다", HttpStatus.CONFLICT),
    USED_PRODUCT_REGION_REQUIRED("USED_012", "조회할 지역을 지정해 주세요", HttpStatus.BAD_REQUEST),
    USED_PRODUCT_NOT_HIDDEN("USED_013", "숨김 처리된 게시글이 아닙니다", HttpStatus.CONFLICT),
    USED_PRODUCT_INVALID_PRICE_RANGE("USED_014", "최소 가격이 최대 가격보다 클 수 없습니다", HttpStatus.BAD_REQUEST),
    // 본인 지정, 정지·탈퇴 계정, 문의한 적 없는 상대를 모두 이 코드로 묶는다.
    // 사유를 나누면 판매자가 임의의 계정 ID로 다른 사용자의 상태를 떠볼 수 있다.
    USED_PRODUCT_INVALID_BUYER("USED_015", "거래 상대로 지정할 수 없는 계정입니다", HttpStatus.BAD_REQUEST),
    // 구매자를 생략한 판매완료 도중 예약 상대가 바뀐 경우. 잠그고 검증한 계정과
    // 실제 예약 상대가 달라지므로, 검증하지 않은 계정을 확정하지 않도록 막고 재시도하게 한다.
    USED_PRODUCT_BUYER_CHANGED("USED_016", "예약 상대가 변경되었습니다. 다시 시도해 주세요", HttpStatus.CONFLICT),

    // 커서는 정렬 키 두 개(createdAt, usedReviewId)를 함께 받아야 한다. 하나만 오면 첫 페이지와
    // 구분할 수 없어 무한 스크롤이 같은 목록을 반복한다 — 조용히 무시하지 않고 알린다.
    USED_REVIEW_INVALID_CURSOR("USED_017", "후기 목록 커서는 작성일시와 후기 ID를 함께 보내야 합니다", HttpStatus.BAD_REQUEST),

    // ===================== 커뮤니티 (COMMUNITY) =====================
    COMMUNITY_POST_NOT_FOUND("COMMUNITY_001", "존재하지 않는 게시글입니다", HttpStatus.NOT_FOUND),
    COMMUNITY_POST_ACCESS_DENIED("COMMUNITY_002", "게시글 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    COMMUNITY_COMMENT_NOT_FOUND("COMMUNITY_003", "존재하지 않는 댓글입니다", HttpStatus.NOT_FOUND),
    COMMUNITY_COMMENT_ACCESS_DENIED("COMMUNITY_004", "댓글 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    COMMUNITY_REPLY_NOT_FOUND("COMMUNITY_005", "존재하지 않는 대댓글입니다", HttpStatus.NOT_FOUND),
    COMMUNITY_REPLY_ACCESS_DENIED("COMMUNITY_006", "대댓글 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    COMMUNITY_POST_LIKE_ALREADY_EXISTS("COMMUNITY_007", "이미 좋아요한 게시글입니다", HttpStatus.CONFLICT),
    COMMUNITY_COMMENT_LIKE_ALREADY_EXISTS("COMMUNITY_008", "이미 좋아요한 댓글입니다", HttpStatus.CONFLICT),
    COMMUNITY_REPLY_DEPTH_EXCEEDED("COMMUNITY_009", "대댓글에는 대댓글을 달 수 없습니다", HttpStatus.BAD_REQUEST),
    COMMUNITY_POST_LIKE_NOT_FOUND("COMMUNITY_010", "좋아요하지 않은 게시글입니다", HttpStatus.NOT_FOUND),
    COMMUNITY_COMMENT_LIKE_NOT_FOUND("COMMUNITY_011", "좋아요하지 않은 댓글입니다", HttpStatus.NOT_FOUND),

    // ===================== 채팅 (CHAT) =====================
    CHAT_ROOM_NOT_FOUND("CHAT_001", "존재하지 않는 채팅방입니다", HttpStatus.NOT_FOUND),
    CHAT_ROOM_ACCESS_DENIED("CHAT_002", "채팅방 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    CHAT_MESSAGE_NOT_FOUND("CHAT_003", "존재하지 않는 메시지입니다", HttpStatus.NOT_FOUND),
    CHAT_MESSAGE_ACCESS_DENIED("CHAT_004", "메시지 삭제 권한이 없습니다", HttpStatus.FORBIDDEN),
    CHAT_ROOM_INACTIVE("CHAT_005", "비활성화된 채팅방입니다", HttpStatus.BAD_REQUEST),
    CHAT_NOT_PARTICIPANT("CHAT_006", "채팅방 참여자가 아닙니다", HttpStatus.FORBIDDEN),
    CHAT_NOT_GROUP_ROOM("CHAT_007", "그룹 채팅방이 아닙니다", HttpStatus.BAD_REQUEST),
    CHAT_MESSAGE_NOT_DELETABLE("CHAT_008", "삭제할 수 없는 메시지입니다", HttpStatus.BAD_REQUEST),
    CHAT_INVALID_ROOM_TYPE("CHAT_009", "지원하지 않는 채팅방 타입입니다", HttpStatus.BAD_REQUEST),
    CHAT_MESSAGE_DUPLICATE("CHAT_010", "이미 처리된 메시지 요청입니다", HttpStatus.CONFLICT),
    CHAT_NAME_REQUIRED("CHAT_011", "채팅방 이름은 필수입니다", HttpStatus.BAD_REQUEST),
    CHAT_ROOM_ALREADY_EXISTS("CHAT_012", "이미 활성화된 채팅방이 존재합니다", HttpStatus.CONFLICT),
    CHAT_ROOM_CLOSE_DENIED("CHAT_013", "채팅방 종료 권한이 없습니다", HttpStatus.FORBIDDEN),
    CHAT_PARTICIPANT_DUPLICATE("CHAT_014", "이미 처리된 채팅방 참여 요청입니다", HttpStatus.CONFLICT),
    CHAT_INVALID_REF_ID("CHAT_015", "채팅방 참조 ID가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    CHAT_SELF_INQUIRY_NOT_ALLOWED("CHAT_016", "본인 게시글에는 문의할 수 없습니다", HttpStatus.BAD_REQUEST),

    // ===================== 리뷰 (REVIEW) =====================
    STORE_REVIEW_NOT_FOUND("REVIEW_001", "존재하지 않는 상점 리뷰입니다", HttpStatus.NOT_FOUND),
    STORE_REVIEW_ACCESS_DENIED("REVIEW_002", "리뷰 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    STORE_REVIEW_ALREADY_EXISTS("REVIEW_003", "이미 리뷰를 작성했습니다", HttpStatus.CONFLICT),
    STORE_REVIEW_ORDER_REQUIRED("REVIEW_004", "구매 완료 후 리뷰를 작성할 수 있습니다", HttpStatus.BAD_REQUEST),
    STORE_REVIEW_REPLY_ALREADY_EXISTS("REVIEW_005", "이미 답글을 작성했습니다", HttpStatus.CONFLICT),
    STORE_REVIEW_RESERVATION_REQUIRED("REVIEW_006", "방문 완료 후 리뷰를 작성할 수 있습니다", HttpStatus.BAD_REQUEST),

    // ===================== 카테고리 (CATEGORY) =====================
    CATEGORY_NOT_FOUND("CATEGORY_001", "존재하지 않는 카테고리입니다", HttpStatus.NOT_FOUND),
    CATEGORY_IN_USE("CATEGORY_002", "사용 중인 카테고리는 삭제할 수 없습니다", HttpStatus.BAD_REQUEST),
    CATEGORY_DUPLICATE("CATEGORY_003", "이미 존재하는 카테고리입니다", HttpStatus.CONFLICT),
    CATEGORY_MAX_DEPTH_EXCEEDED("CATEGORY_004", "카테고리는 최대 3단계까지만 생성할 수 있습니다", HttpStatus.BAD_REQUEST),
    CATEGORY_PARENT_TYPE_MISMATCH("CATEGORY_006", "상위 카테고리와 카테고리 타입이 일치하지 않습니다", HttpStatus.BAD_REQUEST),
    CATEGORY_INVALID_ORDER("CATEGORY_007", "카테고리 순서 정보가 올바르지 않습니다", HttpStatus.BAD_REQUEST),

    // ===================== 찜 (FAVORITE) =====================
    FAVORITE_NOT_FOUND("FAVORITE_001", "존재하지 않는 찜 정보입니다", HttpStatus.NOT_FOUND),
    FAVORITE_ACCESS_DENIED("FAVORITE_002", "찜 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    FAVORITE_ALREADY_EXISTS("FAVORITE_003", "이미 찜한 대상입니다", HttpStatus.CONFLICT),
    FAVORITE_INVALID_PERIOD("FAVORITE_004", "조회 시작일이 종료일보다 늦을 수 없습니다", HttpStatus.BAD_REQUEST),

    // ===================== 알림 (NOTIFICATION) =====================
    NOTIFICATION_NOT_FOUND("NOTIFICATION_001", "존재하지 않는 알림입니다", HttpStatus.NOT_FOUND),
    NOTIFICATION_ACCESS_DENIED("NOTIFICATION_002", "알림 접근 권한이 없습니다", HttpStatus.FORBIDDEN),

    // ===================== 문의 (INQUIRY) =====================
    INQUIRY_NOT_FOUND("INQUIRY_001", "존재하지 않는 문의입니다", HttpStatus.NOT_FOUND),
    INQUIRY_ACCESS_DENIED("INQUIRY_002", "문의 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    INQUIRY_ALREADY_ANSWERED("INQUIRY_004", "이미 답변된 문의입니다", HttpStatus.CONFLICT),
    INQUIRY_STORE_REQUIRED("INQUIRY_005", "상점 문의 시 storeId는 필수입니다", HttpStatus.BAD_REQUEST),
    INQUIRY_TARGET_TYPE_MISMATCH("INQUIRY_006", "해당 문의 유형에 대한 답변 권한이 없습니다", HttpStatus.FORBIDDEN),
    INQUIRY_ANSWER_NOT_FOUND("INQUIRY_007", "존재하지 않는 답변입니다", HttpStatus.NOT_FOUND),
    INQUIRY_STORE_NOT_ALLOWED("INQUIRY_008", "관리자 문의에는 storeId를 포함할 수 없습니다", HttpStatus.BAD_REQUEST),
    INQUIRY_ALREADY_CLOSED("INQUIRY_009", "이미 종료된 문의입니다", HttpStatus.CONFLICT),
    INQUIRY_NOT_CLOSED("INQUIRY_010", "종료 상태가 아닌 문의입니다", HttpStatus.CONFLICT),
    INQUIRY_CLOSED("INQUIRY_011", "종료된 문의에는 답변할 수 없습니다", HttpStatus.CONFLICT),

    // ===================== 신고 (REPORT) =====================
    REPORT_NOT_FOUND("REPORT_001", "존재하지 않는 신고입니다.", HttpStatus.NOT_FOUND),
    REPORT_ACCESS_DENIED("REPORT_002", "신고 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    REPORT_ALREADY_EXISTS("REPORT_003", "이미 신고한 대상입니다.", HttpStatus.CONFLICT),
    REPORT_ALREADY_PROCESSED("REPORT_004", "이미 처리된 신고입니다.", HttpStatus.CONFLICT),
    REPORT_SELF_NOT_ALLOWED("REPORT_005", "자기 자신 또는 자신의 콘텐츠는 신고할 수 없습니다.", HttpStatus.BAD_REQUEST),
    REPORT_ACTION_NOT_ALLOWED("REPORT_006", "해당 신고 대상에 적용할 수 없는 조치입니다.", HttpStatus.BAD_REQUEST),
    REPORT_TARGET_NOT_AVAILABLE("REPORT_007", "신고 대상이 삭제되었거나 조치할 수 없는 상태입니다.", HttpStatus.CONFLICT),

    // ===================== 이미지 (IMAGE) =====================
    IMAGE_INVALID_FORMAT("IMAGE_001", "지원하지 않는 이미지 형식입니다 (jpg, png, webp만 가능)", HttpStatus.BAD_REQUEST),
    IMAGE_SIZE_EXCEEDED("IMAGE_002", "이미지 크기는 10MB를 초과할 수 없습니다", HttpStatus.BAD_REQUEST),
    IMAGE_NOT_FOUND("IMAGE_003", "존재하지 않는 이미지입니다", HttpStatus.NOT_FOUND),
    IMAGE_UPLOAD_FAILED("IMAGE_004", "이미지 업로드에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    IMAGE_LIMIT_EXCEEDED("IMAGE_005", "이미지는 최대 10장까지 등록 가능합니다", HttpStatus.BAD_REQUEST),

    // ===================== 락 (LOCK) =====================
    LOCK_ACQUIRE_FAILED("LOCK_001", "요청이 처리 중입니다. 잠시 후 다시 시도해주세요.", HttpStatus.CONFLICT),
    LOCK_RESERVATION_FAILED("LOCK_002", "예약 처리 중입니다. 잠시 후 다시 시도해주세요.", HttpStatus.CONFLICT),
    LOCK_PAYMENT_FAILED("LOCK_003", "결제 처리 중입니다. 잠시 후 다시 시도해주세요.", HttpStatus.CONFLICT),
    LOCK_ORDER_FAILED("LOCK_004", "주문 처리 중입니다. 잠시 후 다시 시도해주세요.", HttpStatus.CONFLICT),

    // ===================== AI 매니저 (AI) =====================
    AI_PLAN_REQUIRED("AI_001", "현재 플랜에서 사용할 수 없는 기능입니다. 플랜 업그레이드가 필요합니다", HttpStatus.FORBIDDEN),
    AI_USAGE_LIMIT_EXCEEDED("AI_002", "이번 달 AI 사용량을 모두 사용했습니다", HttpStatus.TOO_MANY_REQUESTS),
    AI_MESSAGE_NOT_FOUND("AI_003", "존재하지 않는 AI 생성 메시지입니다", HttpStatus.NOT_FOUND),
    AI_MESSAGE_ALREADY_SENT("AI_004", "이미 발송된 메시지입니다", HttpStatus.CONFLICT),
    AI_MESSAGE_NOT_EDITABLE("AI_005", "수정할 수 없는 메시지 상태입니다", HttpStatus.BAD_REQUEST),
    AI_FORBIDDEN("AI_006", "AI 매니저 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    AI_GENERATION_FAILED("AI_007", "AI 문구 생성에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_INVALID_STATUS("AI_008", "요청을 처리할 수 없는 상태입니다", HttpStatus.CONFLICT),
    AI_INVALID_SCHEDULE_TIME("AI_009", "예약 발송 시간은 현재 시각 이후여야 합니다", HttpStatus.BAD_REQUEST),
    AI_SAVING_PLAN_NOT_FOUND("AI_010", "저장할 절감 계획이 없습니다. 먼저 절감 계획을 생성해 주세요", HttpStatus.NOT_FOUND),
    AI_INVALID_CHANNEL("AI_011", "해당 채널로는 공지를 발송할 수 없습니다", HttpStatus.BAD_REQUEST),
    AI_INVALID_MESSAGE_TYPE("AI_012", "해당 메시지 타입으로는 이 작업을 수행할 수 없습니다", HttpStatus.BAD_REQUEST),
    AI_EXPOSURE_NOT_FOUND("AI_013", "생활권 매칭 노출 이력이 없습니다. 먼저 노출을 시작해 주세요", HttpStatus.NOT_FOUND),
    AI_RATE_LIMITED("AI_014", "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요", HttpStatus.TOO_MANY_REQUESTS),
    AI_DRAFT_LIMIT_EXCEEDED("AI_015", "저장 가능한 초안 개수를 초과했습니다. 가장 오래된 초안을 삭제하고 새 초안을 생성할까요?", HttpStatus.CONFLICT),
    AI_INVALID_TARGET("AI_016", "AI 메시지의 대상 정보가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    AI_PLAN_ALREADY_SUBSCRIBED("AI_017", "이미 구독 중인 플랜입니다", HttpStatus.CONFLICT),
    AI_CHATBOT_INVALID_INPUT("AI_018", "유효하지 않은 챗봇 입력입니다", HttpStatus.BAD_REQUEST),

    // ===================== 입력값 검증 (VALIDATION) =====================
    VALIDATION_INVALID_INPUT("VALIDATION_001", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
