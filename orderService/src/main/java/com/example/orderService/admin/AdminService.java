package com.example.orderService.admin;

import com.example.orderService.config.TossConfig;
import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrderItemRepository;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.common.sse.SseEmitterManager;
import com.example.orderService.payment.entity.Payment;
import com.example.orderService.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final SseEmitterManager sseEmitterManager;
    private final AdminNotifyPublisher adminNotifyPublisher;
    private final TossConfig tossConfig;
    private final RestClient inventoryRestClient;

    /**
     * 주문 수령 처리: DELIVERED 상태 변경 + 고객 SSE + 관리자 브로드캐스트
     */
    public void receiveOrder(String orderNo) {
        Orders order = ordersRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderNo));

        String uuid = order.getSessionUuid();
        order.deliver();
        ordersRepository.save(order);
        log.info("[Admin] 수령 완료: orderNo={}, uuid={}", orderNo, uuid);

        sseEmitterManager.send(uuid, "ORDER_RECEIVED", Map.of("orderNo", orderNo));
        adminNotifyPublisher.orderDelivered(uuid, orderNo);
    }

    /**
     * 환불 전 주문·결제 정보 조회
     *
     * @throws IllegalArgumentException 주문/결제 미존재 또는 이미 환불된 경우
     */
    public Map<String, Object> lookupRefund(String orderNo) {
        Orders order = ordersRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보가 없습니다."));

        if ("CANCELLED".equals(payment.getStatus()))
            throw new IllegalArgumentException("이미 환불된 주문입니다.");

        return Map.of(
                "orderNo",    orderNo,
                "paidAmount", payment.getPaidAmount(),
                "status",     payment.getStatus(),
                "paymentKey", payment.getPaymentKey()
        );
    }

    /**
     * Toss 환불 처리: 금액 검증 → Toss /cancel → DB 상태 CANCELLED
     *
     * @throws IllegalArgumentException 검증 실패 시
     * @throws RuntimeException         Toss API 오류 시
     */
    public void processRefund(String orderNo, int cancelAmount, String cancelReason) {
        Orders order = ordersRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보가 없습니다."));

        if ("CANCELLED".equals(payment.getStatus()))
            throw new IllegalArgumentException("이미 환불된 주문입니다.");
        if (cancelAmount <= 0)
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        if (cancelAmount > payment.getPaidAmount())
            throw new IllegalArgumentException(
                    "환불 금액(" + cancelAmount + "원)이 결제 금액(" + payment.getPaidAmount() + "원)을 초과합니다.");

        try {
            String encoded = Base64.getEncoder()
                    .encodeToString((tossConfig.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));

            inventoryRestClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/" + payment.getPaymentKey() + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", cancelReason, "cancelAmount", cancelAmount))
                    .retrieve()
                    .toBodilessEntity();

            payment.cancel();
            paymentRepository.save(payment);
            order.cancel();
            ordersRepository.save(order);

            log.info("[Refund] 완료: orderNo={}, amount={}", orderNo, cancelAmount);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Refund] Toss 환불 실패: orderNo={}", orderNo, e);
            throw new RuntimeException("환불 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 오늘 판매 로그 조회 (PAID 이상 주문, 최신순)
     */
    public List<Map<String, Object>> getSalesLog() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now        = LocalDateTime.now();

        List<Orders> orders = ordersRepository
                .findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(
                        List.of("PAID", "COOKING", "READY", "DELIVERED", "CANCELLED"),
                        startOfDay, now
                );

        return orders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            String desc = buildDesc(items, order.getOrderNo());

            return Map.<String, Object>of(
                    "orderNo",    order.getOrderNo(),
                    "desc",       desc,
                    "status",     order.getStatus(),
                    "totalPrice", order.getTotalPrice(),
                    "createdAt",  order.getCreatedAt().toString()
            );
        }).collect(Collectors.toList());
    }

    /**
     * 관리자 화면 초기 스냅샷: 진행중(PAID / COOKING / READY) 주문 전체
     */
    public List<Map<String, String>> getSessionSnapshot() {
        List<Orders> activeOrders = ordersRepository
                .findByStatusIn(List.of("PAID", "COOKING", "READY"));

        List<Map<String, String>> result = activeOrders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            String desc = buildDesc(items, order.getOrderNo());

            return Map.of(
                    "uuid",    order.getSessionUuid(),
                    "orderNo", order.getOrderNo(),
                    "desc",    desc,
                    "status",  order.getStatus()
            );
        }).toList();

        log.info("[AdminSnapshot] 진행중 주문 {}건 반환", result.size());
        return result;
    }

    /**
     * inventory-service 전체 재고 목록 조회 (프록시)
     */
    public List<Map<String, Object>> listStock() {
        return inventoryRestClient.get()
                .uri("/api/admin/stock")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    /**
     * inventory-service 재고 delta 조정 (프록시)
     */
    public void adjustStock(Long productId, int delta) {
        log.info("[Stock] 재고 조정 요청: productId={}, delta={}", productId, delta);
        inventoryRestClient.post()
                .uri("/api/admin/stock/" + productId)
                .body(Map.of("delta", delta))
                .retrieve()
                .toBodilessEntity();
    }

    private String buildDesc(List<OrderItem> items, String fallback) {
        if (items.isEmpty()) return fallback;
        if (items.size() == 1) return items.get(0).getProductName();
        return items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";
    }
}
