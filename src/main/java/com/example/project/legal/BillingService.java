package com.example.project.legal;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingService {
    private final IntegrationSettings settings;
    private final PaymentGateway gateway;
    private final PaymentOrderRepository orders;
    private final TransactionTemplate tx;
    public BillingService(IntegrationSettings settings,PaymentGateway gateway,PaymentOrderRepository orders,PlatformTransactionManager manager) {
        this.settings=settings;this.gateway=gateway;this.orders=orders;tx=new TransactionTemplate(manager);
    }
    public record Checkout(String paymentId,String storeId,String channelKey,String orderName,long totalAmount,String currency,int passDays) {}
    private void ready() { if(!settings.billingReady()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"결제 설정이 아직 완료되지 않았습니다. 현재 청구되지 않습니다."); }
    public Checkout create(String user) {
        ready();
        LegalService.require(settings.price()>0&&settings.price()<=1000000&&settings.passDays()>0&&settings.passDays()<=366,"이용권 금액과 기간 설정을 확인해 주세요.");
        // One active pass per account avoids accidental duplicate purchases.
        LegalService.require(activeUntil(user)==null,"이미 이용 중인 이용권이 있습니다. 만료 후 구매해 주세요.");
        var o=new PaymentOrder();o.paymentId="legal-"+UUID.randomUUID();o.username=user;o.amount=settings.price();o.passDays=settings.passDays();o.storeId=settings.value("legal.billing.store-id","");
        orders.saveAndFlush(o);
        return new Checkout(o.paymentId,o.storeId,settings.value("legal.billing.channel-key",""),"판결읽기 Plus "+o.passDays+"일 이용권",o.amount,"CURRENCY_KRW",o.passDays);
    }
    public PaymentOrder verify(String user,String id) {
        ready();
        var order=orders.findById(id).filter(o->o.username.equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        return synchronize(order.paymentId);
    }
    private PaymentOrder synchronize(String id) {
        // Keep the provider lookup inside the lock so concurrent webhook/browser checks cannot apply stale states out of order.
        return tx.execute(status->{
            var order=orders.locked(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            var remote=gateway.fetch(id);
            if(!id.equals(remote.id())||!order.storeId.equals(remote.storeId())||order.amount!=remote.amount()||!"KRW".equals(remote.currency()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"주문과 결제 정보가 일치하지 않습니다. 운영자에게 문의해 주세요.");
            if("PAID".equals(remote.status())) {
                if(remote.paidAt()==null||remote.paidAt().isBefore(order.createdAt.minusSeconds(300))||remote.paidAt().isAfter(Instant.now().plusSeconds(300)))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"결제 시각을 확인할 수 없습니다.");
                order.status="PAID";order.paidAt=remote.paidAt();order.validUntil=remote.paidAt().plus(Duration.ofDays(order.passDays));
            } else { order.status=remote.status();order.validUntil=null; }
            order.verifiedAt=Instant.now();
            return orders.saveAndFlush(order);
        });
    }
    public Instant activeUntil(String user) {
        return orders.findByUsernameAndStatusAndValidUntilAfter(user,"PAID",Instant.now()).stream().map(o->o.validUntil).max(Comparator.naturalOrder()).orElse(null);
    }
    public void requirePremium(String user) {
        if(!settings.flag("legal.billing.enforce-premium")) return;
        ready();
        var active=orders.findByUsernameAndStatusAndValidUntilAfter(user,"PAID",Instant.now());
        for(var order:active) {
            if(order.verifiedAt==null||order.verifiedAt.isBefore(Instant.now().minusSeconds(300))) order=synchronize(order.paymentId);
            if("PAID".equals(order.status)&&order.validUntil!=null&&order.validUntil.isAfter(Instant.now())) return;
        }
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,"Plus 이용권이 필요한 기능입니다. 이용권 페이지에서 확인해 주세요.");
    }
    public void webhook(String body,String id,String signature,String timestamp) {
        ready();
        LegalService.require(body.length()<=65536,"웹훅 요청이 너무 큽니다.");
        String paymentId=gateway.verifyWebhook(body,id,signature,timestamp);
        if(paymentId!=null&&orders.existsById(paymentId)) synchronize(paymentId);
    }
}
