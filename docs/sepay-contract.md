# SePay webhook contract

Pinned 2026-08-24 from official SePay Developer documentation:

- https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc
- https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook
- https://developer.sepay.vn/vi/sepay-webhooks/xu-ly-loi
- https://developer.sepay.vn/vi/sepay-webhooks/cau-hinh-ma-thanh-toan
- https://developer.sepay.vn/vi/sepay-webhooks/tao-qr-va-form-thanh-toan

## Selected authentication

G11 configures SePay `HMAC-SHA256`:

```text
X-SePay-Signature: sha256={hex_hmac_sha256}
X-SePay-Timestamp: {unix_seconds}
input: {timestamp}.{raw_body}
```

Reject timestamp skew above 300 seconds. Compare signature in constant time. Preserve raw bytes before JSON parsing.

## Callback payload

JSON fields used: stable integer `id`; `accountNumber`; nullable `code`; `transferType` (`in` or `out`); positive integer VND `transferAmount`; optional `referenceCode`. Other official fields are accepted but ignored for G11.

`id` is unique across retries/replays. Payment codes are configured in SePay's payment-code recognition and match the exact generated `GYM` code. The G11 webhook must filter inbound transfers and the configured receiving account.

## Success and retry

Success is HTTP 200 or 201 with exact JSON `{"success":true}` within 30 seconds. SePay retries connection failures and non-2xx responses: initial delivery plus Fibonacci waits of 1, 1, 2, 3, 5, 8, and 13 minutes.

## VietQR URL

```text
https://vietqr.app/img?acc={account}&bank={bank}&amount={vnd}&des={urlencoded_payment_code}
```

Payment creates this opaque URL locally. It makes no provider network call and returns no raw QR fields over gRPC.

## Security

Use HTTPS at Kong, HMAC, a bounded body, provider transaction deduplication, receiving-account validation, and SePay IP allowlisting at the deployment boundary. Secrets and raw callback bodies never enter logs or evidence.
