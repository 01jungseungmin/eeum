package com.eeum.eeum.domain.file.enums;

public enum FileObjectStatus {
    CONFIRMED,
    ATTACHED,
    CLEANUP_PENDING,
    // 정리를 재시도 한계까지 실패한 상태. 스케줄러가 더 집지 않으므로 영구 실패 건이
    // 큐 앞을 막지 않는다. 첨부도 계속 금지한다 — S3 객체가 이미 지워졌을 수 있다.
    CLEANUP_FAILED
}
