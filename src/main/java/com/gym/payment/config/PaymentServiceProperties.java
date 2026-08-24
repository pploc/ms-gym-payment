package com.gym.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentServiceProperties(
        OutboxProperties outbox,
        SePayProperties sepay
) {
    public record OutboxProperties(
            int batchSize,
            Duration pollDelay,
            Duration retryDelay,
            int maxAttempts,
            Duration leaseDuration
    ) {}

    public record SePayProperties(
            String webhookSecret,
            String accountNumber,
            String bank,
            String qrAccount,
            String qrBank
    ) {}
}
