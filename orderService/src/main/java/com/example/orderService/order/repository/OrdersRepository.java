package com.example.orderService.order.repository;

import com.example.orderService.order.entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {
    Optional<Orders> findByOrderNo(String orderNo);
    List<Orders> findBySessionUuidAndStatusIn(String sessionUuid, List<String> statuses);
    List<Orders> findByStatusIn(List<String> statuses);
}











































