package com.example.project.legal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="legal_payment_orders")
public class PaymentOrder {
    @Id @Column(length=60) public String paymentId;
    @Column(nullable=false,length=30) public String username;
    @Column(nullable=false,length=100) public String storeId;
    public long amount;
    public int passDays;
    @Column(nullable=false,length=20) public String status="READY";
    public Instant createdAt=Instant.now();
    public Instant paidAt;
    public Instant validUntil;
    public Instant verifiedAt;
    @Version public Long version;
}
