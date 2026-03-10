package com.example.orderService.payment.controller;

import com.example.orderService.config.TossConfig;
import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrderItemRepository;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.payment.dto.PaymentReadyRequest;
import com.example.orderService.payment.dto.PaymentReadyResponse;

import com.example.orderService.payment.dto.StockReserveRequest;
import com.example.orderService.payment.dto.StockReserveResult;
import com.example.orderService.payment.entity.Payment;
import com.example.orderService.message.inventory.manufacture.ManufacturePublisher;
import com.example.orderService.payment.repository.PaymentRepository;
import com.example.orderService.common.redis.SessionRedisManager;
import com.example.orderService.common.sse.SseEmitterManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 결제 컨트롤러
 *
 * POST /api/payment/ready    — 주문 생성 + Toss 결제창 정보 반환
 * GET  /payment/success      — Toss 결제 성공 콜백
 *                              → 재고 선차감 → Toss 최종 승인 → DB 저장 → RabbitMQ
 * GET  /payment/fail         — Toss 결제 실패 콜백
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final TossConfig tossConfig;
    private final RestClient restClient;
    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRedisManager sessionRedisManager;
    private final SseEmitterManager sseEmitterManager;
    private final ManufacturePublisher manufacturePublisher;
    private final com.example.orderService.admin.AdminNotifyPublisher adminNotifyPublisher;

    // ── 1. 주문 생성 + 결제창 정보 반환 ──────────────────────

    @PostMapping("/api/payment/ready")
    public ResponseEntity<PaymentReadyResponse> ready(@RequestBody PaymentReadyRequest request,
                                                      @RequestParam String uuid) {
        if (!sessionRedisManager.exists(uuid)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // orderNo: 세션UUID 앞 8자리 + 시간
        String orderNo = generateOrderNo(uuid);

        // 주문 저장 (PENDING)
        Orders order = Orders.builder()
                .orderNo(orderNo)
                .sessionUuid(uuid)
                .status("PENDING")
                .totalPrice(request.totalPrice())
                .build();
        ordersRepository.save(order);

        // 주문 상품 저장
        for (PaymentReadyRequest.Item item : request.items()) {
            orderItemRepository.save(OrderItem.builder()
                    .order(order)
                    .productId(item.productId())
                    .productName(item.productName())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .build());
        }

        log.info("[Payment] 주문 생성: orderNo={}, amount={}", orderNo, request.totalPrice());

        return ResponseEntity.ok(new PaymentReadyResponse(
                tossConfig.getClientKey(),
                orderNo,
                request.totalPrice(),
                request.orderName()
        ));
    }

    // ── 2. Toss 결제 성공 콜백 ────────────────────────────────

    @GetMapping("/payment/success")
    public RedirectView success(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam int amount) {

        Orders order = ordersRepository.findByOrderNo(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderId));

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        List<StockReserveRequest.Item> stockItems = items.stream()
                .map(i -> new StockReserveRequest.Item(i.getProductId(), i.getQuantity()))
                .toList();

        // 재고 선차감
        StockReserveResult reserveResult = reserveStock(stockItems);
        if (!reserveResult.success()) {
            order.cancel();
            ordersRepository.save(order);
            log.warn("[Payment] 재고 부족으로 주문 취소: orderNo={}", orderId);
            return new RedirectView("/orderView?result=fail&reason=STOCK_INSUFFICIENT");
        }

        // Toss 최종 승인
        boolean confirmed = confirmToss(paymentKey, orderId, amount);
        if (!confirmed) {
            releaseStock(stockItems);
            order.cancel();
            ordersRepository.save(order);
            log.warn("[Payment] Toss 승인 실패: orderNo={}", orderId);
            return new RedirectView("/orderView?result=fail&reason=TOSS_CONFIRM_FAILED");
        }

        // 결제 완료 처리
        order.paid();
        ordersRepository.save(order);

        paymentRepository.save(Payment.builder()
                .order(order)
                .paymentKey(paymentKey)
                .status("COMPLETED")
                .paidAmount(amount)
                .build());

        // uuid는 주문에서 가져옴 (Toss 리다이렉트 시 브라우저 컨텍스트 없으므로)
        String uuid = order.getSessionUuid();

        // 세션 진행중 주문 +1
        sessionRedisManager.incrementActiveOrders(uuid);

        // SSE → 조리 대기 상태 전송
        sseEmitterManager.send(uuid, "COOKING_START", Map.of(
                "orderNo", orderId,
                "status", "COOKING"
        ));

        // RabbitMQ 제조 지시
        manufacturePublisher.publish(orderId, uuid, items);

        // 주문 status PAID → COOKING (스냅샷 구분용)
        order.cooking();
        ordersRepository.save(order);

        // 관리자에게 조리 시작 알림 (fanout 브로드캐스트)
        String desc = items.size() == 1
                ? items.get(0).getProductName()
                : items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";
        adminNotifyPublisher.orderCooking(uuid, orderId, desc, String.valueOf(amount));

        log.info("[Payment] 결제 완료: orderNo={}, amount={}", orderId, amount);
        return new RedirectView("/orderView?result=success&orderNo=" + orderId);
    }

    // ── 3. Toss 결제 실패 콜백 ───────────────────────────────

    @GetMapping("/payment/fail")
    public RedirectView fail(
            @RequestParam String code,
            @RequestParam String message,
            @RequestParam String orderId) {

        ordersRepository.findByOrderNo(orderId).ifPresent(order -> {
            order.cancel();
            ordersRepository.save(order);
        });

        log.warn("[Payment] 결제 실패: orderNo={}, code={}, message={}", orderId, code, message);
        return new RedirectView("/orderView?result=fail&reason=" + code);
    }

    // ── private ──────────────────────────────────────────────

    private String generateOrderNo(String sessionUuid) {
        String prefix = sessionUuid.replace("-", "").substring(0, 8);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        return prefix + "_" + time;
    }

    private StockReserveResult reserveStock(List<StockReserveRequest.Item> items) {
        try {
            return restClient.post()
                    .uri("/api/stock/reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StockReserveRequest(items))
                    .retrieve()
                    .body(StockReserveResult.class);
        } catch (Exception e) {
            log.error("[Payment] 재고 선차감 요청 실패", e);
            return new StockReserveResult(false, null);
        }
    }

    private void releaseStock(List<StockReserveRequest.Item> items) {
        try {
            restClient.post()
                    .uri("/api/stock/release")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StockReserveRequest(items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[Payment] 재고 복구 요청 실패", e);
        }
    }

    private boolean confirmToss(String paymentKey, String orderId, int amount) {
        try {
            String encoded = Base64.getEncoder()
                    .encodeToString((tossConfig.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));

            Map<?, ?> result = restClient.post()
                    .uri(TossConfig.CONFIRM_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(Map.class);

            return result != null;
        } catch (Exception e) {
            log.error("[Payment] Toss 최종 승인 실패", e);
            return false;
        }
    }

}