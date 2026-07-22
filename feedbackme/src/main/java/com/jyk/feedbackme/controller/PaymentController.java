package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.PaymentOrder;
import com.jyk.feedbackme.dto.PaymentConfirmRequest;
import com.jyk.feedbackme.dto.PaymentCreateRequest;
import com.jyk.feedbackme.service.AuthService;
import com.jyk.feedbackme.service.CreditService;
import com.jyk.feedbackme.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
/**
 * FeedbackMe 백엔드의 PaymentController 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.controller 계층의 책임을 담당합니다.
 */
public class PaymentController {

    private final AuthService authService;
    private final PaymentService paymentService;
    private final CreditService creditService;

    public PaymentController(AuthService authService, PaymentService paymentService, CreditService creditService) {
        this.authService = authService;
        this.paymentService = paymentService;
        this.creditService = creditService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(HttpServletRequest request) {
        AppUser user = currentUser(request);
        if (user == null) {
            return unauthorized();
        }
        creditService.grantSignupCreditsIfMissing(user);

        List<Map<String, Object>> orders = paymentService.getOrders(user).stream()
                .map(this::toOrderResponse)
                .toList();

        return ResponseEntity.ok(Map.of(
                "credit", creditService.getSummary(user),
                "products", paymentService.getProducts(),
                "orders", orders,
                "devMode", paymentService.isDevMode()
        ));
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(HttpServletRequest request, @RequestBody PaymentCreateRequest body) {
        AppUser user = currentUser(request);
        if (user == null) {
            return unauthorized();
        }

        try {
            PaymentOrder order = paymentService.createOrder(user, body.productCode());
            return ResponseEntity.ok(Map.of("order", toOrderResponse(order)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(HttpServletRequest request, @RequestBody PaymentConfirmRequest body) {
        AppUser user = currentUser(request);
        if (user == null) {
            return unauthorized();
        }

        try {
            PaymentOrder order = paymentService.confirm(user, body.paymentKey(), body.orderId(), body.amount());
            return ResponseEntity.ok(Map.of(
                    "order", toOrderResponse(order),
                    "credit", creditService.getSummary(user)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/dev-confirm")
    public ResponseEntity<?> devConfirm(HttpServletRequest request, @PathVariable String orderId) {
        AppUser user = currentUser(request);
        if (user == null) {
            return unauthorized();
        }

        try {
            PaymentOrder order = paymentService.confirmDev(user, orderId);
            return ResponseEntity.ok(Map.of(
                    "order", toOrderResponse(order),
                    "credit", creditService.getSummary(user)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private AppUser currentUser(HttpServletRequest request) {
        return authService.getCurrentUser(request).orElse(null);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
    }

    private Map<String, Object> toOrderResponse(PaymentOrder order) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("productCode", order.getProductCode());
        response.put("orderName", order.getOrderName());
        response.put("amount", order.getAmount());
        response.put("creditAmount", order.getCreditAmount());
        response.put("status", order.getStatus().name());
        response.put("requestedAt", order.getRequestedAt().toString());
        response.put("approvedAt", order.getApprovedAt() != null ? order.getApprovedAt().toString() : "");
        return response;
    }
}
