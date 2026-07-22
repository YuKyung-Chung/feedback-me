package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.PaymentOrder;
import com.jyk.feedbackme.domain.PaymentStatus;
import com.jyk.feedbackme.dto.PaymentProduct;
import com.jyk.feedbackme.repository.PaymentOrderRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
/**
 * FeedbackMe 백엔드의 PaymentService 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.service 계층의 책임을 담당합니다.
 */
public class PaymentService {

    private static final List<PaymentProduct> PRODUCTS = List.of(
            new PaymentProduct("CREDIT_1", "분석권 1회", 1, 1900),
            new PaymentProduct("CREDIT_5", "분석권 5회", 5, 7900),
            new PaymentProduct("CREDIT_10", "분석권 10회", 10, 12900)
    );

    private final Map<String, PaymentProduct> productMap = PRODUCTS.stream()
            .collect(Collectors.toMap(PaymentProduct::code, Function.identity()));

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditService creditService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${payments.toss.secret-key:}")
    private String tossSecretKey;

    @Value("${payments.dev-mode:true}")
    private boolean devMode;

    public PaymentService(PaymentOrderRepository paymentOrderRepository, CreditService creditService) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.creditService = creditService;
    }

    public List<PaymentProduct> getProducts() {
        return PRODUCTS;
    }

    public boolean isDevMode() {
        return devMode;
    }

    @Transactional
    public PaymentOrder createOrder(AppUser user, String productCode) {
        PaymentProduct product = productMap.get(productCode);
        if (product == null) {
            throw new IllegalArgumentException("존재하지 않는 상품입니다.");
        }

        PaymentOrder order = PaymentOrder.builder()
                .orderId("FM-" + UUID.randomUUID().toString().replace("-", ""))
                .user(user)
                .productCode(product.code())
                .orderName(product.name())
                .amount(product.amount())
                .creditAmount(product.credits())
                .build();

        return paymentOrderRepository.save(order);
    }

    @Transactional
    public PaymentOrder confirm(AppUser user, String paymentKey, String orderId, int amount) throws Exception {
        PaymentOrder order = getReadyOrder(user, orderId, amount);
        confirmWithToss(paymentKey, orderId, amount);
        order.markPaid(paymentKey);
        creditService.purchaseCredits(user, order);
        return order;
    }

    @Transactional
    public PaymentOrder confirmDev(AppUser user, String orderId) {
        if (!devMode) {
            throw new IllegalStateException("개발 결제를 사용할 수 없습니다.");
        }

        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .filter(found -> found.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == PaymentStatus.PAID) {
            return order;
        }

        if (order.getStatus() != PaymentStatus.READY) {
            throw new IllegalStateException("결제할 수 없는 주문입니다.");
        }

        order.markPaid("dev_" + orderId);
        creditService.purchaseCredits(user, order);
        return order;
    }

    public List<PaymentOrder> getOrders(AppUser user) {
        return paymentOrderRepository.findTop20ByUserOrderByRequestedAtDesc(user);
    }

    private PaymentOrder getReadyOrder(AppUser user, String orderId, int amount) {
        PaymentOrder order = paymentOrderRepository.findById(orderId)
                .filter(found -> found.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (order.getStatus() != PaymentStatus.READY) {
            throw new IllegalStateException("이미 처리된 주문입니다.");
        }

        if (order.getAmount() != amount) {
            order.markFailed();
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        return order;
    }

    private void confirmWithToss(String paymentKey, String orderId, int amount) throws Exception {
        String normalizedSecretKey = tossSecretKey == null ? "" : tossSecretKey.trim();
        if (normalizedSecretKey.isBlank()) {
            throw new IllegalStateException("Toss Payments secret key is missing.");
        }

        String body = """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "amount": %d
                }
                """.formatted(escapeJson(paymentKey), escapeJson(orderId), amount);

        String credential = Base64.getEncoder()
                .encodeToString((normalizedSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tosspayments.com/v1/payments/confirm"))
                .header("Authorization", "Basic " + credential)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Toss payment confirm failed. status=" + response.statusCode() + ", body=" + response.body());
        }

        new JSONObject(response.body());
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
