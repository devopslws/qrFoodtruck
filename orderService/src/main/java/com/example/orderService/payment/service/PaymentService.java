package com.example.orderService.payment.service;

import com.example.orderService.admin.AdminNotifyPublisher;
import com.example.orderService.config.TossConfig;
import com.example.orderService.message.inventory.manufacture.ManufacturePublisher;
import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrderItemRepository;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.payment.dto.PaymentReadyRequest;
import com.example.orderService.payment.dto.PaymentReadyResponse;
import com.example.orderService.payment.dto.StockReserveRequest;
import com.example.orderService.payment.dto.StockReserveResult;
import com.example.orderService.payment.entity.Payment;
import com.example.orderService.payment.exception.StockInsufficientException;
import com.example.orderService.payment.exception.TossConfirmException;
import com.example.orderService.payment.repository.PaymentRepository;
import com.example.orderService.common.redis.SessionRedisManager;
import com.example.orderService.common.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TossConfig tossConfig;
    private final RestClient inventoryRestClient;
    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRedisManager sessionRedisManager;
    private final SseEmitterManager sseEmitterManager;
    private final ManufacturePublisher manufacturePublisher;
    private final AdminNotifyPublisher adminNotifyPublisher;

    /**
     * 주문 생성 (PENDING) + Toss 결제창 정보 반환
     */
    public PaymentReadyResponse createOrder(String uuid, PaymentReadyRequest request) {
        String orderNo = generateOrderNo(uuid);

        Orders order = Orders.builder()
                .orderNo(orderNo)
                .sessionUuid(uuid)
                .status("PENDING")
                .totalPrice(request.totalPrice())
                .build();
        ordersRepository.save(order);

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

        return new PaymentReadyResponse(
                tossConfig.getClientKey(),
                orderNo,
                request.totalPrice(),
                request.orderName()
        );
    }

    /**
     * Toss 결제 성공 처리
     * 재고 선차감 → Toss 최종 승인 → DB 저장 → SSE → RabbitMQ
     *
     * @throws StockInsufficientException 재고 부족 시
     * @throws TossConfirmException       Toss 승인 실패 시
     */
    @Transactional
    public String processPaymentSuccess(String paymentKey, String orderId, int amount) {
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
            throw new StockInsufficientException("재고 부족: " + orderId);
        }

        // Toss 최종 승인
        boolean confirmed = confirmToss(paymentKey, orderId, amount);
        if (!confirmed) {
            releaseStock(stockItems);
            order.cancel();
            ordersRepository.save(order);
            log.warn("[Payment] Toss 승인 실패: orderNo={}", orderId);
            throw new TossConfirmException("Toss 승인 실패: " + orderId);
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

        String uuid = order.getSessionUuid();

        sessionRedisManager.incrementActiveOrders(uuid);

        sseEmitterManager.send(uuid, "COOKING_START", Map.of(
                "orderNo", orderId,
                "status", "COOKING"
        ));

        manufacturePublisher.publish(orderId, uuid, items);

        order.cooking();
        ordersRepository.save(order);

        String desc = items.size() == 1
                ? items.get(0).getProductName()
                : items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";
        adminNotifyPublisher.orderCooking(uuid, orderId, desc, String.valueOf(amount), items);

        log.info("[Payment] 결제 완료: orderNo={}, amount={}", orderId, amount);
        return orderId;
    }

    /**
     * Toss 결제 실패 처리 — 주문 CANCELLED
     */
    public void handlePaymentFail(String orderId) {
        ordersRepository.findByOrderNo(orderId).ifPresent(order -> {
            order.cancel();
            ordersRepository.save(order);
        });
        log.warn("[Payment] 결제 실패 처리: orderNo={}", orderId);
    }

    // ── private ──────────────────────────────────────────────

    private String generateOrderNo(String sessionUuid) {
        String prefix = sessionUuid.replace("-", "").substring(0, 8);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        return prefix + "_" + time;
    }

    private StockReserveResult reserveStock(List<StockReserveRequest.Item> items) {
        try {
            return inventoryRestClient.post()
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
            inventoryRestClient.post()
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

            Map<?, ?> result = inventoryRestClient.post()
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
