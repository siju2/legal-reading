package com.example.project.legal;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegrationSafetyTest {
    @Test void integrationsAreDisabledWithoutConfiguration() {
        var config=new IntegrationSettings(new MockEnvironment());
        assertThat(config.aiReady()).isFalse();assertThat(config.billingReady()).isFalse();
        var env=new MockEnvironment().withProperty("legal.ai.enabled","true").withProperty("legal.billing.enabled","true");
        assertThat(new IntegrationSettings(env).aiReady()).isFalse();assertThat(new IntegrationSettings(env).billingReady()).isFalse();
    }
    @Test void officialSdkAcceptsAuthenticWebhookAndRejectsTamperingAndStaleTimestamp()throws Exception {
        byte[] secret="local-test-secret-only".getBytes(StandardCharsets.UTF_8);
        var config=new IntegrationSettings(new MockEnvironment().withProperty("legal.billing.webhook-secret",Base64.getEncoder().encodeToString(secret)));
        var gateway=new PortOneGateway(config);String timestamp=Long.toString(Instant.now().getEpochSecond());
        String body="{\"type\":\"Transaction.Paid\",\"timestamp\":\"2026-11-03T00:00:00Z\",\"data\":{\"paymentId\":\"legal-test\",\"storeId\":\"store-test\",\"transactionId\":\"tx-test\"}}";
        String signature=sign(secret,"event-test",timestamp,body);
        assertThat(gateway.verifyWebhook(body,"event-test",signature,timestamp)).isEqualTo("legal-test");
        assertThatThrownBy(()->gateway.verifyWebhook(body.replace("legal-test","someone-else"),"event-test",signature,timestamp)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->gateway.verifyWebhook(body,"event-test",signature,"1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->gateway.verifyWebhook(body,null,null,null)).isInstanceOf(ResponseStatusException.class);
    }
    private String sign(byte[] key,String id,String timestamp,String body)throws Exception {
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return "v1,"+Base64.getEncoder().encodeToString(mac.doFinal((id+"."+timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void premiumGateRejectsMissingPassAndAllowsFreshVerifiedPass() {
        var config=new IntegrationSettings(new MockEnvironment().withProperty("legal.billing.enforce-premium","true").withProperty("legal.billing.enabled","true")
            .withProperty("legal.billing.api-secret","fake").withProperty("legal.billing.store-id","store-test").withProperty("legal.billing.channel-key","channel-test").withProperty("legal.billing.webhook-secret","fake"));
        var orders=mock(PaymentOrderRepository.class);var gateway=mock(PaymentGateway.class);
        var service=new BillingService(config,gateway,orders,mock(PlatformTransactionManager.class));
        when(orders.findByUsernameAndStatusAndValidUntilAfter(eq("alice"),eq("PAID"),any())).thenReturn(List.of());
        assertThatThrownBy(()->service.requirePremium("alice")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(402));
        var order=new PaymentOrder();order.status="PAID";order.validUntil=Instant.now().plusSeconds(3600);order.verifiedAt=Instant.now();
        when(orders.findByUsernameAndStatusAndValidUntilAfter(eq("alice"),eq("PAID"),any())).thenReturn(List.of(order));
        service.requirePremium("alice");verifyNoInteractions(gateway);
    }
    @Test void providerFailureCannotBypassPremiumRecheck() {
        var config=new IntegrationSettings(new MockEnvironment().withProperty("legal.billing.enforce-premium","true").withProperty("legal.billing.enabled","true")
            .withProperty("legal.billing.api-secret","fake").withProperty("legal.billing.store-id","store-test").withProperty("legal.billing.channel-key","channel-test").withProperty("legal.billing.webhook-secret","fake"));
        var orders=mock(PaymentOrderRepository.class);var gateway=mock(PaymentGateway.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        var service=new BillingService(config,gateway,orders,manager);var order=new PaymentOrder();order.paymentId="legal-test";order.status="PAID";order.validUntil=Instant.now().plusSeconds(3600);order.verifiedAt=Instant.now().minusSeconds(600);
        when(orders.findByUsernameAndStatusAndValidUntilAfter(eq("alice"),eq("PAID"),any())).thenReturn(List.of(order));when(orders.locked(order.paymentId)).thenReturn(Optional.of(order));
        when(gateway.fetch(order.paymentId)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(()->service.requirePremium("alice")).isInstanceOf(ResponseStatusException.class);verify(gateway).fetch(order.paymentId);
    }
}
