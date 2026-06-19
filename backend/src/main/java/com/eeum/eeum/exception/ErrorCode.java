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

    // ===================== 사장 추가정보 기입 =====================
    OWNER_ALREADY_APPROVED( "OWNER_001","이미 승인된 사장 계정입니다.",HttpStatus.CONFLICT),
    OWNER_REVIEW_ALREADY_REQUESTED("OWNER_002","이미 입점 심사 요청이 접수되었습니다.",HttpStatus.CONFLICT),
    OWNER_CHECKLIST_NOT_COMPLETED( "OWNER_003","입점 심사 필수 항목을 모두 완료해야 합니다.",HttpStatus.BAD_REQUEST),
    OWNER_INFO_NOT_FOUND( "OWNER_004","사장 신청 정보를 찾을 수 없습니다.",HttpStatus.NOT_FOUND),

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

    // ===================== 예약 (RESERVATION) =====================
    RESERVATION_NOT_FOUND("RESERVATION_001", "존재하지 않는 예약입니다", HttpStatus.NOT_FOUND),
    RESERVATION_CAPACITY_EXCEEDED("RESERVATION_002", "예약 가능 인원을 초과했습니다", HttpStatus.BAD_REQUEST),
    RESERVATION_CANCEL_NOT_ALLOWED("RESERVATION_003", "취소할 수 없는 예약 상태입니다", HttpStatus.BAD_REQUEST),
    RESERVATION_ACCESS_DENIED("RESERVATION_004", "예약 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    RESERVATION_TIME_UNAVAILABLE("RESERVATION_005", "해당 시간대는 예약할 수 없습니다", HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_NOT_RESERVABLE("RESERVATION_006","현재 예약할 수 없는 상점입니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_CLOSED_DAY("RESERVATION_007", "해당 요일은 상점 휴무일입니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_OUTSIDE_BUSINESS_HOURS("RESERVATION_008", "상점 영업시간 외에는 예약할 수 없습니다.",HttpStatus.BAD_REQUEST),
    RESERVATION_STORE_BUSINESS_HOURS_NOT_SET("RESERVATION_009", "상점 영업시간이 등록되어 있지 않습니다.",HttpStatus.BAD_REQUEST),
    VISIT_RESERVATION_DUPLICATED("RESERVATION_010","해당 시간에는 이미 방문 예약이 있습니다.",HttpStatus.CONFLICT),
    RESERVATION_TEAM_LIMIT_EXCEEDED("RESERVATION_011","해당 시간대의 예약 가능 팀 수를 초과했습니다.",HttpStatus.CONFLICT),
    RESERVATION_SETTING_NOT_FOUND("RESERVATION_012","예약 설정을 찾을 수 없습니다.",HttpStatus.NOT_FOUND),
    RESERVATION_DISABLED("RESERVATION_013","해당 상점은 방문 예약 기능을 사용하지 않습니다.",HttpStatus.BAD_REQUEST),
    VISIT_RESERVATION_ALREADY_EXISTS("RESERVATION_014","이미 동일시간 예약이 존재합니다.",HttpStatus.CONFLICT),

    // ===================== 중고거래 (USED) =====================
    USED_PRODUCT_NOT_FOUND("USED_001", "존재하지 않는 중고 게시글입니다", HttpStatus.NOT_FOUND),
    USED_PRODUCT_ACCESS_DENIED("USED_002", "중고 게시글 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    USED_PRODUCT_NOT_ON_SALE("USED_003", "판매 중인 상품이 아닙니다", HttpStatus.BAD_REQUEST),
    USED_PRODUCT_ALREADY_RESERVED("USED_004", "이미 예약된 상품입니다", HttpStatus.CONFLICT),
    USED_PRODUCT_ALREADY_SOLD("USED_005", "이미 판매된 상품입니다", HttpStatus.BAD_REQUEST),
    USED_REVIEW_NOT_FOUND("USED_006", "존재하지 않는 중고거래 리뷰입니다", HttpStatus.NOT_FOUND),
    USED_REVIEW_ALREADY_EXISTS("USED_007", "이미 리뷰를 작성했습니다", HttpStatus.CONFLICT),
    USED_REVIEW_NOT_COMPLETED("USED_008", "거래가 완료된 후 리뷰를 작성할 수 있습니다", HttpStatus.BAD_REQUEST),

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

    // ===================== 리뷰 (REVIEW) =====================
    STORE_REVIEW_NOT_FOUND("REVIEW_001", "존재하지 않는 상점 리뷰입니다", HttpStatus.NOT_FOUND),
    STORE_REVIEW_ACCESS_DENIED("REVIEW_002", "리뷰 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    STORE_REVIEW_ALREADY_EXISTS("REVIEW_003", "이미 리뷰를 작성했습니다", HttpStatus.CONFLICT),
    STORE_REVIEW_ORDER_REQUIRED("REVIEW_004", "구매 완료 후 리뷰를 작성할 수 있습니다", HttpStatus.BAD_REQUEST),
    STORE_REVIEW_REPLY_ALREADY_EXISTS("REVIEW_005", "이미 답글을 작성했습니다", HttpStatus.CONFLICT),

    // ===================== 카테고리 (CATEGORY) =====================
    CATEGORY_NOT_FOUND("CATEGORY_001", "존재하지 않는 카테고리입니다", HttpStatus.NOT_FOUND),
    CATEGORY_IN_USE("CATEGORY_002", "사용 중인 카테고리는 삭제할 수 없습니다", HttpStatus.BAD_REQUEST),
    CATEGORY_DUPLICATE("CATEGORY_003", "이미 존재하는 카테고리입니다", HttpStatus.CONFLICT),

    // ===================== 찜 (FAVORITE) =====================
    FAVORITE_NOT_FOUND("FAVORITE_001", "존재하지 않는 찜 정보입니다", HttpStatus.NOT_FOUND),
    FAVORITE_ACCESS_DENIED("FAVORITE_002", "찜 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    FAVORITE_ALREADY_EXISTS("FAVORITE_003", "이미 찜한 대상입니다", HttpStatus.CONFLICT),

    // ===================== 알림 (NOTIFICATION) =====================
    NOTIFICATION_NOT_FOUND("NOTIFICATION_001", "존재하지 않는 알림입니다", HttpStatus.NOT_FOUND),
    NOTIFICATION_ACCESS_DENIED("NOTIFICATION_002", "알림 접근 권한이 없습니다", HttpStatus.FORBIDDEN),

    // ===================== 문의 (INQUIRY) =====================
    INQUIRY_NOT_FOUND("INQUIRY_001", "존재하지 않는 문의입니다", HttpStatus.NOT_FOUND),
    INQUIRY_ACCESS_DENIED("INQUIRY_002", "문의 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    INQUIRY_MODIFY_NOT_ALLOWED("INQUIRY_003", "수정할 수 없는 문의 상태입니다", HttpStatus.BAD_REQUEST),
    INQUIRY_ALREADY_ANSWERED("INQUIRY_004", "이미 답변된 문의입니다", HttpStatus.CONFLICT),

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

    // ===================== 입력값 검증 (VALIDATION) =====================
    VALIDATION_INVALID_INPUT("VALIDATION_001", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}