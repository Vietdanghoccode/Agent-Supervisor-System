# Conversation Service — Giai đoạn 2: Outbox Publisher

## 1. Mục tiêu và phạm vi

Giai đoạn 2 thêm worker đọc `outbox_events` và publish event lên Kafka mà không
thay đổi transaction tạo conversation của giai đoạn 1.

```text
POST /conversations
        |
        v
PostgreSQL: conversations + messages + outbox_events (một transaction)
        |
        v
Outbox Publisher: claim -> publish -> complete/retry
        |
        v
Kafka topic: conversation.events
```

Worker chạy trong Kubernetes Deployment riêng
`conversation-outbox-publisher`, nhưng dùng chung source code và image với
`conversation-service`. API đặt `OUTBOX_PUBLISHER_ENABLED=false`; worker đặt
biến này thành `true`.

Phase này chỉ publish `ConversationCreated`. `MessageCreated`,
`ConversationAssigned`, `ConversationQueued` và `ConversationClosed` chưa được
tạo hoặc publish.

## 2. Delivery semantic và state machine

Publisher bảo đảm **at-least-once**, không phải exactly-once giữa PostgreSQL và
Kafka. Kafka có thể đã nhận record nhưng worker chết trước khi cập nhật DB; khi
lease hết hạn, cùng event sẽ được gửi lại với cùng `eventId`. Consumer phải lưu
và deduplicate theo `eventId`.

```text
                         Kafka ACK
PENDING -> PROCESSING -----------------> PUBLISHED
   ^           |
   |           | publish lỗi
   |           v
   +------ PENDING (next_attempt_at + backoff)

PROCESSING có locked_until hết hạn -> được worker bất kỳ claim lại
```

Worker claim tối đa một batch trong transaction ngắn bằng
`FOR UPDATE SKIP LOCKED`. Transaction kết thúc trước khi gọi Kafka. Mỗi claim:

- Tăng `attempt_count`.
- Ghi `locked_by` duy nhất cho instance worker.
- Đặt `locked_until` để thu hồi event nếu worker chết.
- Chỉ callback có đúng `locked_by` mới được mark thành công hoặc retry.

Publish lỗi trả event về `PENDING`. Retry dùng exponential backoff từ 1 giây,
tối đa 60 giây và không giới hạn số lần thử. Event type không nằm trong danh
sách hỗ trợ không được claim.

## 3. Kafka contract

```text
Topic: conversation.events
Key:   conversationId
```

Record có các header UTF-8:

```text
eventId=<outbox UUID>
eventType=ConversationCreated
contentType=application/json
eventVersion=1
```

Payload giữ nguyên contract đã ghi ở phase 1:

```json
{
  "eventId": "89f1589a-79cf-4e03-ac85-3d4b70fcec35",
  "eventType": "ConversationCreated",
  "conversationId": "8401f632-fcef-43a8-8880-eaa79a15981f",
  "customerId": 1,
  "channel": "webchat",
  "skill": "support",
  "occurredAt": "2026-06-30T06:00:00Z"
}
```

Topic development có 3 partition, replication factor 1. Key theo
`conversationId` đưa record của cùng conversation vào cùng partition. Phase sau
cần bổ sung chính sách aggregate ordering nếu cho phép nhiều event của một
conversation cùng pending, vì retry outbox có thể làm event cũ đến Kafka sau
event mới. Môi trường production cần broker bên ngoài và replication factor phù
hợp thay vì single broker trong repo.

Producer đặt `acks=all` và `enable.idempotence=true`. Idempotent producer xử lý
duplicate do retry ở tầng Kafka producer, nhưng không loại bỏ duplicate phát
sinh giữa Kafka ACK và transaction cập nhật outbox.

## 4. Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `OUTBOX_PUBLISHER_ENABLED` | `false` | Bật scheduler publisher |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Danh sách Kafka broker |
| `OUTBOX_TOPIC` | `conversation.events` | Topic đích |
| `OUTBOX_SUPPORTED_EVENT_TYPES` | `ConversationCreated` | Event type được claim, phân cách bằng dấu phẩy |
| `OUTBOX_POLL_INTERVAL` | `500ms` | Khoảng nghỉ giữa các batch |
| `OUTBOX_METRICS_INTERVAL` | `10s` | Chu kỳ cập nhật backlog metrics |
| `OUTBOX_BATCH_SIZE` | `100` | Số event tối đa mỗi claim |
| `OUTBOX_LEASE_DURATION` | `60s` | Thời hạn ownership của claim |
| `OUTBOX_RETRY_INITIAL_DELAY` | `1s` | Backoff lần đầu |
| `OUTBOX_RETRY_MAX_DELAY` | `60s` | Trần backoff |

Lease nên lớn hơn Kafka `delivery.timeout.ms` (mặc định 30 giây). Khi tăng batch
hoặc thay đổi timeout, cần giữ đủ biên để callback hoàn tất trước khi instance
khác thu hồi event.

Actuator cung cấp `/actuator/health`, `/actuator/metrics` và
`/actuator/prometheus`. Các metric riêng:

```text
outbox.publisher.published
outbox.publisher.retries
outbox.publisher.backlog
outbox.publisher.oldest.age.seconds
```

## 5. Triển khai Minikube

```bash
cd conversation-service
mvn clean package
docker build -t conversation-service:phase2-outbox .
minikube image load --overwrite=true conversation-service:phase2-outbox

cd ..
kubectl apply -f infra/kafka-deployment.yaml
kubectl rollout status deployment/kafka --timeout=180s
kubectl wait --for=condition=complete \
  job/kafka-create-conversation-topic --timeout=180s

kubectl apply -f infra/conversation-deployment.yaml
kubectl rollout status deployment/conversation-postgres --timeout=180s
kubectl rollout status deployment/conversation-service --timeout=180s
kubectl rollout status deployment/conversation-outbox-publisher --timeout=180s
```

Nếu Job tạo topic đã tồn tại nhưng cần chạy lại sau khi thay broker, xóa Job rồi
apply lại manifest:

```bash
kubectl delete job kafka-create-conversation-topic --ignore-not-found
kubectl apply -f infra/kafka-deployment.yaml
```

## 6. Smoke test và vận hành

Tạo conversation qua Gateway như phase 1, sau đó kiểm tra state:

```bash
kubectl exec deployment/conversation-postgres -- \
  psql -U postgres -d conversation_db -c \
  'SELECT id, event_type, status, attempt_count, next_attempt_at,
          locked_by, locked_until, published_at, last_error
   FROM outbox_events ORDER BY created_at;'
```

Đọc record và in cả key/header:

```bash
kubectl exec deployment/kafka -- \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic conversation.events --from-beginning \
  --property print.key=true --property print.headers=true \
  --max-messages 1
```

Xem log retry và metrics:

```bash
kubectl logs deployment/conversation-outbox-publisher -f
kubectl port-forward deployment/conversation-outbox-publisher 8083:8083
curl http://localhost:8083/actuator/prometheus | grep outbox_publisher
```

Để kiểm tra khả năng phục hồi, scale Kafka về 0, tạo conversation, xác nhận API
vẫn trả `201` và outbox chuyển qua retry; scale Kafka lên 1 rồi kiểm tra event
cuối cùng thành `PUBLISHED`:

```bash
kubectl scale deployment/kafka --replicas=0
kubectl scale deployment/kafka --replicas=1
kubectl rollout status deployment/kafka --timeout=180s
kubectl delete job kafka-create-conversation-topic --ignore-not-found
kubectl apply -f infra/kafka-deployment.yaml
kubectl wait --for=condition=complete \
  job/kafka-create-conversation-topic --timeout=180s
```

Kafka development đang dùng storage tạm của pod, nên topic phải được bootstrap
lại sau khi pod bị xóa. Đây không phải cấu hình lưu trữ dành cho production.

Không xóa thủ công event `PENDING`/`PROCESSING`. Nếu backlog hoặc tuổi event cũ
nhất tăng liên tục, kiểm tra Kafka connectivity, `last_error`, topic và producer
timeout trước khi scale thêm worker. Có thể scale publisher; `SKIP LOCKED` và
lease cho phép nhiều replica xử lý song song.
