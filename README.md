# ms-gym-payment

Java 26 / Spring Boot 4 Payment service. G11 implements only Member-owned `MEMBERSHIP` purchase intents through SePay VietQR and publishes `payment.completed.v1` through a transactional outbox.

## Local dependencies

```bash
./gradlew startEnv
./gradlew stopEnv
```

`startEnv` starts PostgreSQL (`payment_db`, port `5434`), Kafka (`9094`), and Schema Registry (`8083`).

## Required runtime configuration

- `SEPAY_WEBHOOK_SECRET`: HMAC-SHA256 secret configured in SePay.
- `SEPAY_ACCOUNT_NUMBER`: receiving account required on inbound callbacks.
- `SEPAY_BANK`: bank code/name used for QR generation.
- `SEPAY_QR_ACCOUNT` / `SEPAY_QR_BANK`: optional QR overrides.
- `PAYMENT_GRPC_SERVER_CERT`, `PAYMENT_GRPC_SERVER_KEY`, `PAYMENT_GRPC_CLIENT_CA`: mTLS materials. Production never permits plaintext.

Generate local mTLS material with `./scripts/generate-local-certs.sh`. It creates a server certificate and the `client-member` identity accepted by Payment.

Webhook: `POST /api/v1/payments/webhook/sepay`. It verifies `X-SePay-Signature` and `X-SePay-Timestamp` over exact raw request bytes using `timestamp.body`, accepts only inbound transfers, and returns `{"success":true}` for valid replays.

No public Payment HTTP API, refunds, history, reports, or failed/refunded event producers exist in G11.
