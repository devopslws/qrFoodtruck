package com.example.orderService.payment.controller;

import com.example.orderService.common.redis.SessionRedisManager;
import com.example.orderService.payment.dto.PaymentReadyRequest;
import com.example.orderService.payment.dto.PaymentReadyResponse;
import com.example.orderService.payment.exception.StockInsufficientException;
import com.example.orderService.payment.exception.TossConfirmException;
import com.example.orderService.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 결제 컨트롤러 — HTTP 바인딩 전담
 *
 * POST /api/payment/ready    — 주문 생성 + Toss 결제창 정보 반환
 * GET  /payment/success      — Toss 결제 성공 콜백 → 서비스 위임 후 redirect
 * GET  /payment/fail         — Toss 결제 실패 콜백 → 서비스 위임 후 redirect
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SessionRedisManager sessionRedisManager;

    @PostMapping("/api/payment/ready")
    public ResponseEntity<PaymentReadyResponse> ready(@RequestBody PaymentReadyRequest request,
                                                      @RequestParam String uuid) {
        if (!sessionRedisManager.exists(uuid)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(paymentService.createOrder(uuid, request));
    }

    @GetMapping("/payment/success")
    public RedirectView success(@RequestParam String paymentKey,
                                @RequestParam String orderId,
                                @RequestParam int amount) {
        try {
            String orderNo = paymentService.processPaymentSuccess(paymentKey, orderId, amount);
            return new RedirectView("/orderView?result=success&orderNo=" + orderNo);
        } catch (StockInsufficientException e) {
            return new RedirectView("/orderView?result=fail&reason=STOCK_INSUFFICIENT");
        } catch (TossConfirmException e) {
            return new RedirectView("/orderView?result=fail&reason=TOSS_CONFIRM_FAILED");
        }
    }

    @GetMapping("/payment/fail")
    public RedirectView fail(@RequestParam String code,
                             @RequestParam String message,
                             @RequestParam String orderId) {
        paymentService.handlePaymentFail(orderId);
        log.warn("[Payment] 결제 실패: orderNo={}, code={}, message={}", orderId, code, message);
        return new RedirectView("/orderView?result=fail&reason=" + code);
    }
}
