# Agent Service Waiting Queue — Giai đoạn 4

## 1. Mục tiêu và luồng xử lý

Giai đoạn 4 giữ nguyên capacity/reservation của Phase 3 và bổ sung hàng đợi bền
vững trên Redis. Khi chưa có agent phù hợp, `POST /agents/reserve` trả `202` cùng
`conversationId`; phía gọi dùng ID này để polling hoặc hủy request.

```text
POST /agents/reserve
        |
        +-- có agent/capacity --> RESERVED (luồng Phase 3)
        |
        +-- không có agent ----> WAITING (FIFO theo skill)
                                      |
                  agent có slot <-----+
                                      v
                                  RESERVED
```

Dispatcher chạy sau khi agent online, chuyển available, release conversation,
reservation hết hạn, hoặc profile/capacity thay đổi. Một agent có nhiều skill sẽ
lấy request có sequence nhỏ nhất trong các đầu queue phù hợp. Vì vậy request skill
khác không chặn nhau, nhưng thứ tự đến vẫn công bằng trong tập request agent có thể
phục vụ.

## 2. Redis data model và invariant

```text
waiting_request_sequence                -> counter toàn cục
waiting_requests:{skill}                -> ZSET conversation_id, score=sequence
request:{conversation_id}               -> HASH
  skill, status, sequence, source,
  created_at, agent_id

reservation:{conversation_id}           -> agent_id, TTL
reservation_expiry_index                -> ZSET conversation_id, score=expire_at_ms
available_agents:{skill}                -> SET agent_id
```

Đây là sáu nhóm key dành cho queue/reservation; các key `agent:{agent_id}:*` vẫn
được dùng cho profile, state và capacity. Không còn
`reservation_owner:{conversation_id}` hoặc `assignment:{conversation_id}`.
Conversation service là nguồn dữ liệu chính cho quan hệ conversation-agent sau
confirm.

State của request gồm `WAITING`, `RESERVED`, `CONFIRMED`, `CANCELLED`, `EXPIRED`
và `RELEASED`. `WAITING`, `RESERVED` và `CONFIRMED` không có retention TTL vì
request vẫn cần cho polling, expiry hoặc release idempotent. Chỉ metadata terminal
`CANCELLED`, `EXPIRED`, `RELEASED` được giữ theo `WAITING_REQUEST_RETENTION`, mặc
định 24 giờ.

Các Lua script đảm bảo:

1. Một `conversationId` chỉ có một request; retry cùng skill trả state hiện tại.
2. Retry cùng ID nhưng khác skill trả `409`.
3. Enqueue, lấy khỏi queue, tăng counter và tạo reservation không bị quan sát ở
   trạng thái nửa hoàn tất.
4. Dispatcher lấp đầy capacity và luôn chọn head có sequence nhỏ nhất trong các
   skill của agent.
5. Agent chỉ ở pool khi `online`, `available` và `current < max`.
6. Reservation sinh từ queue hết hạn được đưa lại queue với sequence cũ. Direct
   reservation hết hạn chuyển thành `EXPIRED`.
7. Tạo reservation đồng thời ghi deadline vào `reservation_expiry_index`.
   Confirm/release/expiry đồng thời xóa member khỏi index.
8. Keyspace listener xử lý nhanh expired event; scheduler quét index định kỳ để
   recovery nếu service downtime hoặc Redis Pub/Sub bỏ lỡ event. Cùng một Lua
   script idempotent bảo vệ khỏi hoàn capacity hoặc requeue hai lần.

## 3. API và ví dụ sử dụng

Tạo request khi chưa có agent:

```bash
CONVERSATION_ID=$(uuidgen)

curl -i -X POST http://localhost:8080/agents/reserve \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CONVERSATION_ID\",\"skill\":\"support\"}"
```

Response `202 Accepted`:

```json
{
  "conversationId": "<uuid>",
  "agentId": null,
  "status": "WAITING",
  "reservationTtlSeconds": 0
}
```

Polling và confirm khi đã nhận `RESERVED`:

```bash
curl http://localhost:8080/reservations/$CONVERSATION_ID
curl -X POST http://localhost:8080/reservations/$CONVERSATION_ID/confirm
```

Hủy một request đang chờ:

```bash
curl -X DELETE http://localhost:8080/reservations/$CONVERSATION_ID
```

DELETE là idempotent với `CANCELLED`; request đã `RESERVED` hoặc `CONFIRMED` trả
`409`. Lookup ID không tồn tại/hết retention trả `404`. UUID hoặc payload sai trả
`400 application/problem+json`.

## 4. Cấu hình và giới hạn recovery

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `RESERVATION_TTL` | `30s` | Thời hạn giữ slot trước confirm |
| `RESERVATION_RECONCILIATION_INTERVAL` | `1s` | Chu kỳ quét reservation expiry index |
| `WAITING_REQUEST_RETENTION` | `24h` | Thời gian giữ metadata terminal |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

Waiting queue và expiry index nằm trong Redis AOF/PVC giống Phase 3. Keyspace
notification `Ex` giảm độ trễ bình thường, còn expiry index là nguồn recovery bền
vững vì Pub/Sub không replay. Không chỉnh counter hoặc index thủ công ngoài quy
trình troubleshooting có kiểm soát.

## 5. Build và triển khai lại lên Minikube/Kubernetes

Chạy test và build image mới:

```bash
cd agent-service
mvn clean package
docker build -t agent-service:phase4-simplified-redis .
minikube image load --overwrite=true agent-service:phase4-simplified-redis
```

Lần chuyển data model này chủ động xóa database Redis cũ. Redis deployment này
phải dành riêng cho agent-service. Lệnh `FLUSHDB` làm mất profile/state agent,
waiting queue và reservation hiện tại; phải đăng ký/đưa agent online lại sau
rollout.

```bash
kubectl scale deployment/agent-service --replicas=0
kubectl rollout status deployment/agent-service --timeout=180s
kubectl exec deployment/agent-redis -- redis-cli FLUSHDB

cd ..
kubectl apply -f infra/agent-deployment.yaml
kubectl scale deployment/agent-service --replicas=1
kubectl rollout status deployment/agent-redis --timeout=180s
kubectl rollout status deployment/agent-service --timeout=180s
```

Nếu cần cập nhật Gateway sau khi agent-service sẵn sàng:

```bash
kubectl rollout restart deployment/spring-cloud-gateway
kubectl rollout status deployment/spring-cloud-gateway --timeout=180s
```

Xác nhận deployment:

```bash
kubectl get pods -l app=agent-service
kubectl logs deployment/agent-service --tail=200
kubectl port-forward deployment/agent-service 8084:8084
curl http://localhost:8084/actuator/health/readiness
curl http://localhost:8084/v3/api-docs
```

Swagger aggregate vẫn ở `http://$(minikube ip)/swagger-ui.html`; OpenAPI qua
Gateway ở `/api/agent/v3/api-docs`.

## 6. Smoke test và troubleshooting

Tạo request WAITING, kiểm tra Redis rồi đưa agent online:

```bash
CONVERSATION_ID=$(uuidgen)
curl -X POST http://localhost:8084/agents/reserve \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CONVERSATION_ID\",\"skill\":\"support\"}"

kubectl exec deployment/agent-redis -- redis-cli \
  ZRANGE waiting_requests:support 0 -1 WITHSCORES
kubectl exec deployment/agent-redis -- redis-cli \
  HGETALL request:$CONVERSATION_ID
kubectl exec deployment/agent-redis -- redis-cli \
  ZSCORE reservation_expiry_index $CONVERSATION_ID

curl -X PUT http://localhost:8084/agents/101/profile \
  -H 'Content-Type: application/json' \
  -d '{"maxConversations":2,"skills":["support"],"teams":[],"channels":[]}'
curl -X POST http://localhost:8084/agents/101/online
curl http://localhost:8084/reservations/$CONVERSATION_ID
```

Confirm, release và xác nhận model không sinh key cũ:

```bash
curl -X POST http://localhost:8084/reservations/$CONVERSATION_ID/confirm
curl -X POST http://localhost:8084/agents/101/release \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CONVERSATION_ID\"}"

kubectl exec deployment/agent-redis -- redis-cli --scan \
  --pattern 'reservation_owner:*'
kubectl exec deployment/agent-redis -- redis-cli --scan \
  --pattern 'assignment:*'
```

Hai lệnh scan cuối phải không trả kết quả. Sau confirm, reservation và expiry
index member phải biến mất nhưng `request:{id}` vẫn là `CONFIRMED`; sau release
request chuyển `RELEASED` và có retention TTL.

Nếu request không được gán, kiểm tra lần lượt `request:{id}`, ZSET đúng skill,
profile skills, state/status, current/max và `available_agents:{skill}`. Nếu expiry
không requeue, kiểm tra `ZSCORE reservation_expiry_index <id>`, cấu hình scheduler,
và `CONFIG GET notify-keyspace-events` phải chứa `E` và `x`, sau đó xem log của
agent-service. Scheduler vẫn phải recovery được khi expired event bị bỏ lỡ.
