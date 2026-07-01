# Conversation Service — Giai đoạn 3: Assignment Worker

## 1. Mục tiêu

Giai đoạn này thêm worker trong Conversation Service để đồng bộ trạng thái
conversation với Agent Service. Worker chạy bằng scheduler đơn giản, claim các
conversation cần assign, gọi Agent Service để reserve/confirm agent, rồi cập
nhật PostgreSQL.

```text
POST /conversations
        |
        v
conversations.status = WAITING
        |
        v
Assignment Worker: WAITING/QUEUED -> ASSIGNING
        |
        +-- POST /agents/reserve
        +-- GET /reservations/{conversationId}
        +-- POST /reservations/{conversationId}/confirm
        v
QUEUED hoặc ASSIGNED
```

## 2. Status lifecycle

Conversation Service có các status:

| Status | Ý nghĩa |
| --- | --- |
| `WAITING` | Conversation mới tạo, chưa gửi hoặc sẽ retry reserve |
| `ASSIGNING` | Worker đã claim row và đang gọi Agent Service |
| `QUEUED` | Agent Service đã đưa request vào waiting queue |
| `ASSIGNED` | Reservation đã confirm và `agent_id` đã được ghi vào DB |
| `CLOSED` | Conversation đã đóng |

Các luồng chính:

```text
WAITING -> ASSIGNING -> ASSIGNED
WAITING -> ASSIGNING -> QUEUED
QUEUED  -> ASSIGNING -> ASSIGNED
QUEUED  -> ASSIGNING -> QUEUED
*       -> CLOSED
```

Worker claim bằng `FOR UPDATE SKIP LOCKED` và cập nhật row sang `ASSIGNING`
ngay trong DB. Các update sau đó chỉ thành công nếu row vẫn đang `ASSIGNING`,
nhờ vậy close API hoặc worker khác không bị ghi đè.

Nếu worker chết khi row đang `ASSIGNING`, row được claim lại sau
`ASSIGNMENT_ASSIGNING_TIMEOUT`.

## 3. Agent Service contract

Worker gọi các API hiện có của Agent Service:

```text
POST   /agents/reserve
GET    /reservations/{conversationId}
POST   /reservations/{conversationId}/confirm
DELETE /reservations/{conversationId}
POST   /agents/{agentId}/release
```

Reserve request:

```json
{
  "conversationId": "8401f632-fcef-43a8-8880-eaa79a15981f",
  "skill": "support"
}
```

Nếu conversation không có `skill`, worker dùng `ASSIGNMENT_DEFAULT_SKILL`
mặc định là `support`.

Mapping response:

| Agent status | Conversation update |
| --- | --- |
| `WAITING` | `QUEUED`, `agent_id = null` |
| `RESERVED` | gọi confirm, sau đó `ASSIGNED` |
| `CONFIRMED` | `ASSIGNED` nếu response có `agentId` |

Lỗi tạm thời từ Agent Service đưa conversation về trạng thái retryable trước
khi claim: `WAITING` hoặc `QUEUED`.

## 4. Close API

Conversation Service thêm endpoint:

```text
POST /conversations/{conversationId}/close
```

Close đồng bộ với Agent Service trước khi đổi DB sang `CLOSED`:

| Current status | Hành động trước khi close |
| --- | --- |
| `WAITING` | Không cần gọi Agent Service |
| `QUEUED` | `DELETE /reservations/{conversationId}` |
| `ASSIGNED` | `POST /agents/{agentId}/release` |
| `ASSIGNING` | Lookup reservation; nếu reserved thì confirm rồi release |
| `CLOSED` | Trả resource hiện tại, idempotent |

Nếu cleanup Agent Service thất bại, API không chuyển DB sang `CLOSED`.
Conflict trả `409 application/problem+json`; lỗi service trả `502`.

## 5. Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `ASSIGNMENT_WORKER_ENABLED` | `false` | Bật scheduler assignment worker |
| `AGENT_SERVICE_URL` | `http://agent-service` | Base URL Agent Service |
| `ASSIGNMENT_DEFAULT_SKILL` | `support` | Skill dùng khi conversation thiếu skill |
| `ASSIGNMENT_POLL_INTERVAL` | `1s` | Chu kỳ scheduler |
| `ASSIGNMENT_BATCH_SIZE` | `50` | Số conversation claim mỗi batch |
| `ASSIGNMENT_ASSIGNING_TIMEOUT` | `30s` | Tuổi tối đa của row `ASSIGNING` trước khi recover |
| `ASSIGNMENT_CONNECT_TIMEOUT` | `2s` | HTTP connect timeout tới Agent Service |
| `ASSIGNMENT_READ_TIMEOUT` | `5s` | HTTP read timeout tới Agent Service |

Trong Kubernetes dev, `conversation-service` bật worker bằng
`ASSIGNMENT_WORKER_ENABLED=true`. Có thể tách worker thành deployment riêng dùng
cùng image nếu muốn scale độc lập; claim DB vẫn an toàn cho nhiều instance.

## 6. Triển khai và kiểm tra

Build và load image:

```bash
cd conversation-service
mvn clean package
docker build -t conversation-service:phase3-assignment .
minikube image load --overwrite=true conversation-service:phase3-assignment
```

Apply service:

```bash
cd ..
kubectl apply -f infra/agent-deployment.yaml
kubectl apply -f infra/conversation-deployment.yaml
kubectl rollout status deployment/agent-service --timeout=180s
kubectl rollout status deployment/conversation-service --timeout=180s
```

Tạo conversation:

```bash
curl -i -X POST http://localhost:8080/conversations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: customer-1-request-001' \
  -d '{"customerId":1,"message":"Tôi cần hỗ trợ","channel":"webchat","skill":"support"}'
```

Kiểm tra DB:

```bash
kubectl exec deployment/conversation-postgres -- \
  psql -U postgres -d conversation_db -c \
  'SELECT id, status, agent_id, skill, updated_at FROM conversations ORDER BY created_at DESC LIMIT 10;'
```

Close conversation:

```bash
curl -i -X POST http://localhost:8080/conversations/$CONVERSATION_ID/close
```

Chạy test:

```bash
cd conversation-service
mvn test
```

Nếu conversation kẹt `ASSIGNING`, kiểm tra log của `conversation-service`, URL
Agent Service, và giá trị `ASSIGNMENT_ASSIGNING_TIMEOUT`. Sau timeout, worker sẽ
claim lại row để retry.
