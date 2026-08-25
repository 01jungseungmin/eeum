package com.eeum.eeum.application.used.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.used.event.UsedProductSoldEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 거래 완료 후기 요청 알림.
 *
 * <p>{@code USED_REVIEW}는 "리뷰 요청"이므로 수신자가 후기를 쓸 사람(구매자)이어야 한다.
 * 판매자에게 보내면 뜻이 반대가 되고, {@code NotificationSettings}가 이 타입을
 * {@code usedProductEnabled}로 판정하므로 사용자의 알림 설정도 의도와 다르게 걸린다.
 */
@ExtendWith(MockitoExtension.class)
class UsedProductSoldNotificationListenerTest {

    private static final Long PRODUCT_ID = 25L;
    private static final Long BUYER_ID = 7L;

    @Mock NotificationService notificationService;

    @InjectMocks UsedProductSoldNotificationListener listener;

    @Test
    void 후기를_쓸_구매자에게_리뷰_요청_알림을_보낸다() {
        listener.onSold(new UsedProductSoldEvent(PRODUCT_ID, BUYER_ID, "자전거"));

        NotificationCreateRequestDto sent = captured();
        assertThat(sent.getAccountId()).isEqualTo(BUYER_ID);
        assertThat(sent.getType()).isEqualTo(NotificationType.USED_REVIEW);
    }

    @Test
    void 참조는_후기가_아니라_게시글을_가리킨다() {
        // 이 시점에는 후기가 아직 없어 USED_REVIEW로 가리킬 대상이 없다.
        // 사용자가 이동해야 할 곳도 후기를 쓸 게시글이다.
        listener.onSold(new UsedProductSoldEvent(PRODUCT_ID, BUYER_ID, "자전거"));

        NotificationCreateRequestDto sent = captured();
        assertThat(sent.getRefType()).isEqualTo(NotificationRefType.USED_PRODUCT);
        assertThat(sent.getRefId()).isEqualTo(PRODUCT_ID);
        // 게시글 상세가 아니라 후기 작성 화면으로 보낸다 — 삭제된 글에도 후기를 쓸 수 있는데
        // 상세는 404를 주기 때문이다.
        assertThat(sent.getLinkUrl()).isEqualTo("/used/" + PRODUCT_ID + "/reviews");
    }

    @Test
    void 알림_본문에_거래한_게시글_제목을_담는다() {
        // 어떤 거래의 후기를 요청하는지 알 수 없으면 알림이 무의미하다.
        listener.onSold(new UsedProductSoldEvent(PRODUCT_ID, BUYER_ID, "자전거"));

        assertThat(captured().getContent()).contains("자전거");
    }

    private NotificationCreateRequestDto captured() {
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotification(captor.capture());
        return captor.getValue();
    }
}
