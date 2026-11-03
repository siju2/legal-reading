package com.example.project.legal;

import java.time.Instant;

public interface PaymentGateway {
    record Snapshot(String id,String storeId,String status,long amount,String currency,Instant paidAt) {}
    Snapshot fetch(String paymentId);
    String verifyWebhook(String body,String id,String signature,String timestamp);
}
