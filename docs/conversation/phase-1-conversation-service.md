# Conversation Service — Giai đoạn 1

## 1. Phạm vi

Giai đoạn này cung cấp một API duy nhất:

```text
POST /conversations
```

Mỗi request hợp lệ tạo đồng thời:

1. Một conversation có `status = WAITING` và `agent_id = null`.
2. Message đầu tiên có `sender_type = CUSTOMER`.
3. Outbox event có `event_type = ConversationCreated` và `status = PENDING`.

Chưa có outbox publisher, Kafka, Routing Service, Agent Service hoặc queue.

## 2. Database và transaction

Flyway quản lý ba bảng trong database riêng `conversation_db`. Hibernate chạy với
`ddl-auto: validate`, vì vậy ứng dụng không tự thay đổi schema.

```text
BEGIN
  INSERT conversations
  INSERT messages
  INSERT outbox_events
COMMIT
```

Ba thao tác nằm trong cùng một local database transaction. Nếu ghi message hoặc
outbox event thất bại, conversation cũng bị rollback.

### `conversations`

- UUID primary key.
- `customer_id`, `channel`, `skill` chứa context ban đầu.
- `status` luôn là `WAITING` trong phase 1.
- `agent_id` luôn null.
- `idempotency_key` có unique constraint.
- `request_hash` là SHA-256 của payload đã chuẩn hóa.

### `messages`

- UUID primary key và foreign key tới `conversations`.
- Message đầu tiên giữ nguyên nội dung khách hàng gửi.
- `sender_type = CUSTOMER`.

### `outbox_events`

- Payload JSONB chứa `eventId`, `eventType`, `conversationId`, `customerId`,
  `channel`, `skill`, `occurredAt`.
- `status = PENDING`, `published_at = null` cho tới phase triển khai publisher.

## 3. API

Header `Idempotency-Key` là bắt buộc, không được rỗng và tối đa 255 ký tự.

```bash
curl -i -X POST http://$(minikube ip)/conversations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: customer-1-request-001' \
  -d '{
    "customerId": 1,
    "message": "Tôi cần hỗ trợ",
    "channel": "webchat",
    "skill": "support"
  }'
```

`customerId`, `message`, `channel` bắt buộc; `skill` tùy chọn. `channel` và
`skill` được trim, còn message được lưu nguyên văn. Response thành công trả
`201 Created`:

```json
{
  "id": "c96936e8-67a1-4f6c-9bf9-a26d22ec9b82",
  "customerId": 1,
  "status": "WAITING",
  "agentId": null,
  "channel": "webchat",
  "skill": "support",
  "initialMessage": {
    "id": "033339e1-516a-41f9-9910-36d9af2b421b",
    "senderType": "CUSTOMER",
    "content": "Tôi cần hỗ trợ",
    "createdAt": "2026-06-28T18:00:00Z"
  },
  "createdAt": "2026-06-28T18:00:00Z"
}
```

Response luôn có `Idempotency-Replayed: false|true`.

### Quy tắc idempotency

- Key mới: tạo một conversation/message/event và trả `201`.
- Cùng key, cùng payload chuẩn hóa: trả lại resource ban đầu với
  `Idempotency-Replayed: true`, không ghi thêm dữ liệu.
- Cùng key, payload khác: trả `409 application/problem+json`.
- Hai key khác nhau luôn được xem là hai conversation, kể cả payload giống nhau.
- Unique constraint đảm bảo hai request đồng thời cùng key chỉ tạo một bộ dữ liệu.

Thiếu header, body không hợp lệ hoặc validation thất bại trả
`400 application/problem+json`.

## 4. Chạy local và test

Khởi động PostgreSQL local, ví dụ:

```bash
docker run --rm --name conversation-postgres-local \
  -e POSTGRES_DB=conversation_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 postgres:15-alpine
```

Trong terminal khác:

```bash
cd conversation-service
mvn test
mvn spring-boot:run
```

Test integration dùng PostgreSQL thật qua Testcontainers. Cấu hình Surefire đặt
Docker API 1.44 để tương thích Docker Engine mới không còn chấp nhận API 1.32.

## 5. Triển khai Minikube

```bash
cd conversation-service
mvn clean package
docker build -t conversation-service:phase1-openapi .

cd ../api-gateway
mvn clean package
docker build -t gateway-service:conversation-phase1 .

minikube image load --overwrite=true conversation-service:phase1-openapi
minikube image load --overwrite=true gateway-service:conversation-phase1

cd ..
kubectl apply -f infra/conversation-deployment.yaml
kubectl apply -f infra/gateway-deployment.yaml
kubectl rollout restart deployment/spring-cloud-gateway
kubectl rollout status deployment/conversation-postgres --timeout=180s
kubectl rollout status deployment/conversation-service --timeout=180s
kubectl rollout status deployment/spring-cloud-gateway --timeout=180s
```

Swagger UI tại `http://$(minikube ip)/swagger-ui.html`; OpenAPI của service tại
`http://$(minikube ip)/api/conversation/v3/api-docs`.

Khi truy cập Gateway bằng port-forward tại `http://localhost:8080`, OpenAPI dùng
`http://localhost:8080` làm server URL nên Swagger `Try it out` sẽ gọi đúng cổng.
Có thể đổi URL này bằng biến môi trường `OPENAPI_SERVER_URL`.

Kiểm tra dữ liệu:

```bash
kubectl exec deployment/conversation-postgres -- \
  psql -U postgres -d conversation_db -c \
  'SELECT id, status, agent_id, idempotency_key FROM conversations;'

kubectl exec deployment/conversation-postgres -- \
  psql -U postgres -d conversation_db -c \
  'SELECT conversation_id, sender_type, content FROM messages;'

kubectl exec deployment/conversation-postgres -- \
  psql -U postgres -d conversation_db -c \
  'SELECT event_type, status, payload FROM outbox_events;'
```
