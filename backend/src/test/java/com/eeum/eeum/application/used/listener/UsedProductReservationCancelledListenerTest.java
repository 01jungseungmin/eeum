package com.eeum.eeum.application.used.listener;

import com.eeum.eeum.application.notification.dto.request.NotificationCreateRequestDto;
import com.eeum.eeum.application.notification.service.NotificationService;
import com.eeum.eeum.domain.notification.enums.NotificationRefType;
import com.eeum.eeum.domain.notification.enums.NotificationType;
import com.eeum.eeum.domain.used.event.UsedProductReservationCancelledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 판매자 비활성화로 예약이 취소됐음을 구매자에게 알린다.
 *
 * <p>이 통보가 없으면 구매자는 약속한 거래가 사라진 것을 알 방법이 없다 —
 * 판매자가 비활성이 되는 순간 게시글이 모든 조회에서 빠지기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
class UsedProductReservationCancelledListenerTest {

    private static final Long PRODUCT_ID = 25L;
    private static final Long BUYER_ID = 7L;

    @Mock NotificationService notificationService;

    @InjectMocks UsedProductReservationCancelledListener listener;

    @Test
    void 예약_상대였던_구매자에게_알린다() {
        listener.onReservationCancelled(event());

        NotificationCreateRequestDto sent = captured();
        assertThat(sent.getAccountId()).isEqualTo(BUYER_ID);
        assertThat(sent.getType()).isEqualTo(NotificationType.SYSTEM_NOTICE);
    }

    @Test
    void 어떤_거래가_취소됐는지_제목으로_알린다() {
        // 여러 건을 예약 중이면 제목이 없는 알림은 어느 거래인지 알 수 없다.
        listener.onReservationCancelled(event());

        assertThat(captured().getContent()).contains("자전거");
    }

    @Test
    void 취소_사유로_판매자의_제재_여부를_드러내지_않는다() {
        // 같은 이벤트가 탈퇴와 정지 양쪽에서 온다. "탈퇴"라고 단정하면 정지 경로에서 사실과 다르고,
        // 반대로 "정지"라고 쓰면 제3자에게 판매자의 제재 이력을 알리는 셈이 된다.
        listener.onReservationCancelled(event());

        assertThat(captured().getContent())
                .doesNotContain("탈퇴")
                .doesNotContain("정지");
    }

    @Test
    void 링크는_두지_않는다() {
        // 판매자가 비활성이라 게시글 상세는 어차피 404다. 참조는 남겨 어떤 글인지 추적만 가능하게 한다.
        listener.onReservationCancelled(event());

        NotificationCreateRequestDto sent = captured();
        assertThat(sent.getLinkUrl()).isNull();
        assertThat(sent.getRefType()).isEqualTo(NotificationRefType.USED_PRODUCT);
        assertThat(sent.getRefId()).isEqualTo(PRODUCT_ID);
    }

    private UsedProductReservationCancelledEvent event() {
        return new UsedProductReservationCancelledEvent(PRODUCT_ID, BUYER_ID, "자전거");
    }

    private NotificationCreateRequestDto captured() {
        ArgumentCaptor<NotificationCreateRequestDto> captor =
                ArgumentCaptor.forClass(NotificationCreateRequestDto.class);
        verify(notificationService).createNotification(captor.capture());
        return captor.getValue();
    }
}
