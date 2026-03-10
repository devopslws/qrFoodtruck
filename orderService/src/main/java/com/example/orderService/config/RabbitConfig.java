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
 * Exchange: foodtruck.exchange (Direct)
 * Queue:    manufacture.queue  — inventory-service 제조 지시 수신
 * Queue:    cooking.done.queue — order-service 조리완료 알림 수신
 */
@Configuration
public class RabbitConfig {

    // Exchange
    public static final String EXCHANGE = "foodtruck.exchange";

    // Routing Keys
    public static final String ROUTING_MANUFACTURE = "manufacture";
    public static final String ROUTING_COOKING_DONE = "cooking.done";

    // Queues
    public static final String QUEUE_MANUFACTURE  = "manufacture.queue";
    public static final String QUEUE_COOKING_DONE = "cooking.done.queue";

    @Bean
    public DirectExchange foodtruckExchange() {
        return new DirectExchange(EXCHANGE);
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