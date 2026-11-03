package com.example.project.legal;

import java.util.concurrent.TimeUnit;
import io.portone.sdk.server.payment.*;
import io.portone.sdk.server.webhook.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class PortOneGateway implements PaymentGateway {
    private final IntegrationSettings settings;
    public PortOneGateway(IntegrationSettings settings) { this.settings=settings; }
    @Override public Snapshot fetch(String id) {
        try(var client=new PaymentClient(settings.value("legal.billing.api-secret",""),"https://api.portone.io",settings.value("legal.billing.store-id",""))) {
            var task=client.getPayment(id);
            Payment payment;
            try { payment=task.get(15,TimeUnit.SECONDS); }
            finally { if(!task.isDone()) task.cancel(true); }
            if(payment instanceof PaidPayment paid) return new Snapshot(paid.getId(),paid.getStoreId(),"PAID",paid.getAmount().getTotal(),paid.getCurrency().getValue(),paid.getPaidAt());
            if(payment instanceof Payment.Recognized recognized) {
                String state=payment instanceof CancelledPayment||payment instanceof PartialCancelledPayment?"CANCELLED":"PENDING";
                return new Snapshot(recognized.getId(),recognized.getStoreId(),state,recognized.getAmount().getTotal(),recognized.getCurrency().getValue(),null);
            }
            throw new IllegalStateException("Unsupported payment response");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch(Exception e) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"결제 상태를 확인하지 못했습니다. 잠시 후 결제 내역에서 다시 확인해 주세요."); }
    @Override public String verifyWebhook(String body,String id,String signature,String timestamp) {
        try {
            var event=new WebhookVerifier(settings.value("legal.billing.webhook-secret","")).verify(body,id,signature,timestamp);
            return event instanceof WebhookTransaction tx?tx.getData().getPaymentId():null;
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"웹훅 서명을 확인할 수 없습니다."); }
    }
}
