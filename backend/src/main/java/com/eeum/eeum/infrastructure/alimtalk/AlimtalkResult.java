package com.eeum.eeum.infrastructure.alimtalk;

public record AlimtalkResult(
        boolean success,
        String providerMessageId,
        String errorCode
) {
    public static AlimtalkResult ok(String providerMessageId) {
        return new AlimtalkResult(true, providerMessageId, null);
    }

    public static AlimtalkResult fail(String errorCode) {
        return new AlimtalkResult(false, null, errorCode);
    }
}
