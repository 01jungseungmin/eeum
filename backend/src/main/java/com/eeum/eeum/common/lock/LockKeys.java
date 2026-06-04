package com.eeum.eeum.common.lock;

public final class LockKeys {

    private LockKeys() {
    }

    public static String order(Long orderId) {
        return "lock:order:" + orderId;
    }

    public static String orderNumber(String orderNumber) {
        return "lock:order-number:" + orderNumber;
    }

    public static String payment(String paymentId) {
        return "lock:payment:" + paymentId;
    }

    public static String portonePayment(String portonePaymentId) {
        return "lock:portone-payment:" + portonePaymentId;
    }
}