package com.eeum.eeum.infrastructure.alimtalk;

import com.eeum.eeum.common.util.MaskingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

// local/test/발표용 Mock — 실제 알림톡 발송 없이 성공 처리 (전화번호는 마스킹 로그)
@Slf4j
@Component
@ConditionalOnProperty(name = "alimtalk.provider", havingValue = "mock", matchIfMissing = true)
public class MockAlimtalkAdapter implements AlimtalkAdapter {

    @Override
    public AlimtalkResult send(AlimtalkMessage message) {
        if (message.templateCode() == null || message.templateCode().isBlank()) {
            return AlimtalkResult.fail("TEMPLATE_NOT_CONFIGURED");
        }
        log.info("[MockAlimtalk] 알림톡 발송 스킵: phone={}, template={}, title={}",
                MaskingUtil.maskPhone(message.phone()), message.templateCode(), message.title());
        return AlimtalkResult.ok("mock-alimtalk-" + UUID.randomUUID());
    }
}
