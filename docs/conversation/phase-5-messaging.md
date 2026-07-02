# Conversation Service — Giai đoạn 5: Messaging

## 1. Mục tiêu và kiến trúc

Giai đoạn 5 lưu toàn bộ hội thoại trong PostgreSQL, sắp xếp bằng sequence theo
conversation, chống duplicate khi client retry và phát `MessageCreated` qua
transactional outbox.

```text
Client -- Bearer JWT --> Gateway -- X-User-Id/X-User-Role --> Conversation Service
                                                            |
                                                            +-- conversations/messages
                                                            +-- outbox_events
```

Gateway xác minh JWT HS256, expiry và hai claims `userId`, `role`. Hai header
identity do client tự gửi luôn bị xóa trước khi Gateway tạo header mới. Các port
service là nội bộ; client công khai phải đi qua Gateway.

## 2. Data model và transaction

Migration `V4__add_conversation_messaging.sql` bổ sung cho `messages`:

| Field | Ý nghĩa |
| --- | --- |
| `sender_id` | `userId` lấy từ JWT, lưu dạng chuỗi |
| `client_message_id` | ID do client sinh để retry an toàn |
| `content_type` | Hiện chỉ hỗ trợ `text/plain` |
| `message_seq` | Sequence tăng dần trong một conversation |
| `status` | Hiện là `SENT` |
| `updated_at` | Thời điểm cập nhật message |

`conversations` có thêm `last_message_seq` và `last_message_at`. Migration
backfill message cũ theo `(created_at, id)`, lấy sender từ `customer_id`, rồi cập
nhật counter tương ứng. Unique index
`(conversation_id, sender_id, client_message_id)` chống retry tạo bản ghi trùng.

Transaction gửi message lock conversation bằng `FOR UPDATE`, kiểm tra quyền và
status, tăng counter, insert message, cập nhật conversation và insert outbox.
Do đó hai request đồng thời không thể nhận cùng sequence.

## 3. Authentication và authorization

Access token do Auth Service phát có dạng claims:

```json
{"sub":"customer@example.com","userId":1,"role":"customer","exp":1782939000}
```

Rule participant:

- Customer chỉ đọc, gửi và đóng conversation có `customer_id` bằng token.
- Agent chỉ đọc và đóng conversation có `agent_id` bằng token.
- Supervisor không có quyền participant trong phase này.
- Tạo conversation chỉ dành cho role `customer`.

Rule gửi message:

| Status | Customer | Agent |
| --- | --- | --- |
| `WAITING` | Cho phép | Không |
| `QUEUED` | Cho phép | Không |
| `ASSIGNING` | Cho phép | Không |
| `ASSIGNED` | Cho phép | Cho phép nếu đúng `agent_id` |
| `CLOSED` | Không | Không |

Thiếu/sai/hết hạn JWT trả `401`; không phải participant trả `403`; status không
cho gửi hoặc retry cùng ID với payload khác trả `409`.

## 4. API contract

Mọi ví dụ dưới đây dùng `Authorization: Bearer $ACCESS_TOKEN` qua Gateway.

### Tạo conversation

```http
POST /conversations
Idempotency-Key: create-001
Content-Type: application/json

{
  "clientMessageId": "web-001",
  "message": "Tôi cần hỗ trợ",
  "channel": "webchat",
  "skill": "support"
}
```

`customerId` được lấy từ JWT. Message đầu tiên có sequence 1. Transaction ghi
cả `ConversationCreated` và `MessageCreated`.

### Gửi message

```http
POST /conversations/{conversationId}/messages
Content-Type: application/json

{
  "clientMessageId": "web-002",
  "content": "Tôi bổ sung thông tin",
  "contentType": "text/plain"
}
```

Message mới trả `201` và `Idempotency-Replayed: false`. Retry cùng sender, ID,
content và content type trả resource cũ với `200` và
`Idempotency-Replayed: true`. Cùng ID nhưng payload khác trả `409`.

Response message gồm `id`, `senderType`, `senderId`, `clientMessageId`, `content`,
`contentType`, `messageSeq`, `status`, `createdAt`, `updatedAt`.

### Đọc messages

```http
GET /conversations/{conversationId}/messages?afterSeq=123&limit=50
GET /conversations/{conversationId}/messages?beforeSeq=123&limit=50
```

`limit` mặc định 50, tối đa 100. `afterSeq` và `beforeSeq` là exclusive và không
được dùng đồng thời. Response luôn sắp xếp sequence tăng dần, gồm `messages`,
`nextAfterSeq`, `nextBeforeSeq`, `hasMore`.

### Inbox agent

```http
GET /conversation/agent/me?limit=50
GET /conversation/agent/me?limit=50&cursor={opaqueCursor}
```

Chỉ agent được gọi. API trả các conversation `ASSIGNED` của agent trong token,
sắp xếp theo hoạt động mới nhất. Cursor opaque chứa `lastMessageAt` và ID để thứ
tự ổn định khi nhiều conversation cùng timestamp.

## 5. Outbox event

`MessageCreated` được ghi cùng transaction với message:

```json
{
  "eventId": "7b07aef2-73e6-47c6-a72a-78b0886ee9c4",
  "eventType": "MessageCreated",
  "messageId": "f04900c1-d404-41ab-9c87-2604737c8893",
  "conversationId": "60c51bd3-e240-49f1-bbf8-bc9549ef871d",
  "senderType": "CUSTOMER",
  "senderId": "1",
  "clientMessageId": "web-002",
  "contentType": "text/plain",
  "content": "Tôi bổ sung thông tin",
  "messageSeq": 2,
  "status": "SENT",
  "occurredAt": "2026-07-01T12:00:00Z"
}
```

Publisher phải có `OUTBOX_SUPPORTED_EVENT_TYPES=ConversationCreated,MessageCreated`.

## 6. Build, deploy và smoke test

```bash
cd conversation-service
mvn clean package
docker build -t conversation-service:phase5-messaging .

cd ../api-gateway
mvn clean package
docker build -t gateway-service:phase5-messaging .

minikube image load --overwrite=true conversation-service:phase5-messaging
minikube image load --overwrite=true gateway-service:phase5-messaging

cd ..
kubectl apply -f infra/conversation-deployment.yaml
kubectl apply -f infra/auth-deployment.yaml
kubectl apply -f infra/gateway-deployment.yaml
kubectl rollout status deployment/conversation-service --timeout=180s
kubectl rollout status deployment/conversation-outbox-publisher --timeout=180s
kubectl rollout status deployment/spring-cloud-gateway --timeout=180s
```

Đăng nhập lấy access token, sau đó:

```bash
ACCESS_TOKEN='...'

curl -i -X POST http://localhost:8080/conversations \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Idempotency-Key: phase5-create-001' \
  -H 'Content-Type: application/json' \
  -d '{"clientMessageId":"m-001","message":"Xin hỗ trợ","channel":"webchat","skill":"support"}'

curl -i -X POST "http://localhost:8080/conversations/$CONVERSATION_ID/messages" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"clientMessageId":"m-002","content":"Thông tin bổ sung","contentType":"text/plain"}'

curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:8080/conversations/$CONVERSATION_ID/messages?afterSeq=0&limit=50"
```

Nếu nhận `401`, kiểm tra cùng một `JWT_SECRET` ở Auth Service và Gateway, expiry
và prefix `Bearer`. Nếu sequence không tăng hoặc thiếu event, kiểm tra migration,
`last_message_seq`, transaction rollback và outbox publisher. Nếu inbox rỗng,
kiểm tra conversation thực sự là `ASSIGNED` và `agent_id` trùng claim `userId`.
