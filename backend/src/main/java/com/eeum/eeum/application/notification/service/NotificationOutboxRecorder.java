package com.eeum.eeum.application.notification.service;

import com.eeum.eeum.domain.notification.entity.NotificationOutbox;
import com.eeum.eeum.domain.notification.repository.NotificationOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 요청을 원 트랜잭션에 실어 남긴다.
 *
 * <p>{@code Propagation.MANDATORY}인 이유가 핵심이다. 이 기록은 원 작업(메시지 저장 등)과
 * <b>같은 트랜잭션에서 커밋돼야</b> 의미가 있다. 트랜잭션 없이 호출되면 그 보장이 사라지므로
 * 조용히 넘어가지 않고 즉시 실패시킨다 — 잘못 쓰는 것을 배포 전에 드러낸다.
 *
 * <p>직렬화 실패도 예외로 던져 원 작업을 롤백시킨다. 알림을 남길 수 없다면 그 사실을
 * 삼키는 것보다 요청을 실패시키는 편이 낫다 — 삼키면 예전의 조용한 유실로 되돌아간다.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxRecorder {

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, Object payload) {
        try {
            outboxRepository.save(
                    NotificationOutbox.pending(eventType, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "알림 outbox 직렬화 실패 — 엔티티가 아니라 스칼라만 담은 이벤트여야 한다: "
                            + eventType, e);
        }
    }
}
