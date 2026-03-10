package com.example.orderService.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Toss Payments 설정
 *
 * application.yml:
 *   toss:
 *     client-key: ${TOSS_CLIENT_KEY}
 *     secret-key: ${TOSS_SECRET_KEY}
 */
@Getter
@Configuration
public class TossConfig {

    @Value("${toss.client-key}")
    private String clientKey;

    @Value("${toss.secret-key}")
    private String secretKey;

    // Toss 최종 승인 URL
    public static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
}