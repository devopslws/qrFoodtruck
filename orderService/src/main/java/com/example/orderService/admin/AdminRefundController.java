package com.example.orderService.admin;

import com.example.orderService.config.TossConfig;
import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.payment.entity.Payment;
import com.example.orderService.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * GET  /api/admin/refund/lookup?orderNo=  — 주문 결제 정보 조회
 * POST /api/admin/refund                  — Toss 부분/전액 환불
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/refund")
public class AdminRefundController {

    private final OrdersRepository  ordersRepository;
    private final PaymentRepository paymentRepository;
    private final TossConfig        tossConfig;
    private final RestClient        restClient;

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String orderNo) {
        Orders order = ordersRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null)
            return ResponseEntity.badRequest().body(Map.of("error", "주문을 찾을 수 없습니다."));

        Payment payment = paymentRepository.findByOrder(order).orElse(null);
        if (payment == null)
            return ResponseEntity.badRequest().body(Map.of("error", "결제 정보가 없습니다."));

        if ("CANCELLED".equals(payment.getStatus()))
            return ResponseEntity.badRequest().body(Map.of("error", "이미 환불된 주문입니다."));

        return ResponseEntity.ok(Map.of(
                "orderNo",    orderNo,
                "paidAmount", payment.getPaidAmount(),
                "status",     payment.getStatus(),
                "paymentKey", payment.getPaymentKey()
        ));
    }

    @PostMapping
    public ResponseEntity<?> refund(@RequestBody RefundRequest req) {
        Orders order = ordersRepository.findByOrderNo(req.orderNo()).orElse(null);
        if (order == null)
            return ResponseEntity.badRequest().body(Map.of("error", "주문을 찾을 수 없습니다."));

        Payment payment = paymentRepository.findByOrder(order).orElse(null);
        if (payment == null)
            return ResponseEntity.badRequest().body(Map.of("error", "결제 정보가 없습니다."));

        if ("CANCELLED".equals(payment.getStatus()))
            return ResponseEntity.badRequest().body(Map.of("error", "이미 환불된 주문입니다."));
        if (req.cancelAmount() <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "환불 금액은 0보다 커야 합니다."));
        if (req.cancelAmount() > payment.getPaidAmount())
            return ResponseEntity.badRequest().body(Map.of("error",
                    "환불 금액(" + req.cancelAmount() + "원)이 결제 금액(" + payment.getPaidAmount() + "원)을 초과합니다."));

        try {
            String encoded = Base64.getEncoder()
                    .encodeToString((tossConfig.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));

            restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/" + payment.getPaymentKey() + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", req.cancelReason(), "cancelAmount", req.cancelAmount()))
                    .retrieve()
                    .toBodilessEntity();

            payment.cancel();
            paymentRepository.save(payment);
            order.cancel();
            ordersRepository.save(order);

            log.info("[Refund] 완료: orderNo={}, amount={}", req.orderNo(), req.cancelAmount());
            return ResponseEntity.ok(Map.of("message", "환불이 완료되었습니다."));

        } catch (Exception e) {
            log.error("[Refund] Toss 환불 실패: orderNo={}", req.orderNo(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "환불 처리 중 오류가 발생했습니다."));
        }
    }

    public record RefundRequest(String orderNo, int cancelAmount, String cancelReason) {}
}