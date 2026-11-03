package com.example.project.legal;

import java.security.Principal;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BillingController {
    private final IntegrationSettings settings;
    private final BillingService billing;
    private final PaymentOrderRepository orders;
    public BillingController(IntegrationSettings settings,BillingService billing,PaymentOrderRepository orders) {this.settings=settings;this.billing=billing;this.orders=orders;}
    @GetMapping("/billing") String page(Principal user,Model model) {
        model.addAttribute("billingReady",settings.billingReady());model.addAttribute("price",settings.price());model.addAttribute("passDays",settings.passDays());
        model.addAttribute("premiumRequired",settings.flag("legal.billing.enforce-premium"));model.addAttribute("validUntil",billing.activeUntil(user.getName()));
        model.addAttribute("orders",orders.findTop20ByUsernameOrderByCreatedAtDesc(user.getName()));return "legal/billing";
    }
    @PostMapping("/billing/orders") @ResponseBody ResponseEntity<?> create(Principal user) {
        try {return ResponseEntity.ok(billing.create(user.getName()));} catch(ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"결제 요청 실패":e.getReason()));}
    }
    @PostMapping("/billing/verify") String verify(@RequestParam String paymentId,Principal user,RedirectAttributes flash) {
        var order=billing.verify(user.getName(),paymentId);
        flash.addFlashAttribute("notice","PAID".equals(order.status)?"결제를 확인했습니다. 이용권이 활성화되었습니다.":"현재 결제 상태: "+order.status);return "redirect:/billing";
    }
    @GetMapping("/billing/return") String returned(@RequestParam(required=false) String paymentId,Model model) {
        model.addAttribute("paymentId",paymentId);return "legal/billing-return";
    }
    @PostMapping("/billing/webhook") @ResponseBody ResponseEntity<Void> webhook(@RequestBody String body,
        @RequestHeader(value="webhook-id",required=false) String id,@RequestHeader(value="webhook-signature",required=false) String signature,
        @RequestHeader(value="webhook-timestamp",required=false) String timestamp) {
        billing.webhook(body,id,signature,timestamp);return ResponseEntity.noContent().build();
    }
}
