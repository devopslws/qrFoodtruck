package com.example.orderService.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정 (order-service)
 *
 * [기존] foodtruck.exchange (Direct)
 *   manufacture.queue  — inventory-service 제조 지시
 *   cooking.done.queue — 조리완료 알림
 *
 * [신규] admin.notify.exchange (Fanout)
 *   exclusive queue    — Task 시작 시 자동 생성, 종료 시 자동 삭제
 *   수평확장 시 각 Task가 독립 Queue를 가짐 → 전체 브로드캐스트
 */
@Configuration
public class RabbitConfig {

    // ── 기존 Direct Exchange ─────────────────────────────────
    public static final String EXCHANGE          = "foodtruck.exchange";
    public static final String ROUTING_MANUFACTURE  = "manufacture";
    public static final String ROUTING_COOKING_DONE = "cooking.done";
    public static final String QUEUE_MANUFACTURE    = "manufacture.queue";
    public static final String QUEUE_COOKING_DONE   = "cooking.done.queue";

    // ── 관리자 브로드캐스트 Fanout Exchange ───────────────────
    public static final String ADMIN_NOTIFY_EXCHANGE = "admin.notify.exchange";

    @Bean
    public DirectExchange foodtruckExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public FanoutExchange adminNotifyExchange() {
        return new FanoutExchange(ADMIN_NOTIFY_EXCHANGE);
    }

    // exclusive=true: 이 연결에서만 사용, 연결 종료 시 Queue 자동 삭제
    // autoDelete=true: 구독자 없으면 자동 삭제
    // → Task 1개 = Queue 1개, Task N개 = Queue N개 (fanout이 각각 복사 전달)
    @Bean
    public Queue adminNotifyQueue() {
        return QueueBuilder.nonDurable()
                .exclusive()
                .autoDelete()
                .build();
    }

    @Bean
    public Binding adminNotifyBinding() {
        return BindingBuilder.bind(adminNotifyQueue())
                .to(adminNotifyExchange());
    }

    @Bean
    public Queue manufactureQueue() {
        return QueueBuilder.durable(QUEUE_MANUFACTURE).build();
    }

    @Bean
    public Queue cookingDoneQueue() {
        return QueueBuilder.durable(QUEUE_COOKING_DONE).build();
    }

    @Bean
    public Binding manufactureBinding() {
        return BindingBuilder.bind(manufactureQueue())
                .to(foodtruckExchange())
                .with(ROUTING_MANUFACTURE);
    }

    @Bean
    public Binding cookingDoneBinding() {
        return BindingBuilder.bind(cookingDoneQueue())
                .to(foodtruckExchange())
                .with(ROUTING_COOKING_DONE);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
