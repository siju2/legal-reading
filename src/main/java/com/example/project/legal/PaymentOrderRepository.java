package com.example.project.legal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder,String> {
    List<PaymentOrder> findTop20ByUsernameOrderByCreatedAtDesc(String user);
    List<PaymentOrder> findByUsernameAndStatusAndValidUntilAfter(String user,String status,Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.paymentId=:id")
    Optional<PaymentOrder> locked(@Param("id") String id);
}
