# Agent Service + Redis — Giai đoạn 3

## 1. Mục tiêu và phạm vi

Agent Service quản lý presence, trạng thái nhận việc, profile routing và capacity
realtime của agent. Phase 3 chỉ dùng `skill` để chọn agent; `team` và `channel`
được lưu trong profile để dùng ở phase sau.

Redis là nguồn sự thật của hot state trong phase này. Pool
`available_agents:{skill}` chỉ là index ứng viên; Lua luôn kiểm tra lại state,
status và counter trước khi cấp slot.

```text
Routing Service
      |
      | POST /agents/reserve
      v
Agent Service -> Redis Lua: check -> INCR -> cleanup pools -> reservation TTL
```

## 2. State machine

Connectivity và availability là hai lớp độc lập:

```text
agent:{id}:state  = online | offline
agent:{id}:status = available | waiting_to_break | break
```

| State/status | Nhận conversation mới | Có trong skill pool |
| --- | --- | --- |
| offline / bất kỳ status | Không | Không |
| online / available | Có, nếu `current < max` | Có, nếu `current < max` |
| online / waiting_to_break | Không | Không |
| online / break | Không | Không |

Offline không xóa status. Khi online lại, agent tiếp tục status trước đó. Request
break chuyển thẳng sang `break` nếu current bằng 0; nếu còn việc thì chuyển sang
`waiting_to_break`. Release cuối cùng tự chuyển `waiting_to_break -> break`.

## 3. Redis data model

```text
agent:{agent_id}:state                  -> online | offline
agent:{agent_id}:status                 -> available | waiting_to_break | break
agent:{agent_id}:current_conversations  -> integer >= 0
agent:{agent_id}:max_conversations      -> integer >= 1
agent:{agent_id}:skills                 -> SET
agent:{agent_id}:teams                  -> SET
agent:{agent_id}:channels               -> SET

available_agents:{skill}                -> SET agent_id
reservation:{conversation_id}           -> agent_id, TTL mặc định 30s
reservation_owner:{conversation_id}     -> agent_id, metadata cho expiry listener
assignment:{conversation_id}            -> agent_id sau confirm
```

`reservation_owner` cần tồn tại lâu hơn reservation vì Redis expired event chỉ
chứa tên key, không còn value. Confirm/release xóa metadata này. Assignment marker
cho phép release xác minh đúng agent và biến request lặp thành no-op.

Redis chạy AOF `appendfsync everysec` trên PVC. Cấu hình
`notify-keyspace-events Ex` là bắt buộc để Agent Service nhận expired event.

## 4. Atomic invariants

Reserve chạy hoàn toàn trong một Lua script:

1. Nếu conversation đã reserved/confirmed, trả lại cùng agent mà không tăng counter.
2. Lấy ngẫu nhiên ứng viên từ `available_agents:{skill}`.
3. Gate bắt buộc: `state=online`, `status=available`, `current < max`.
4. Chỉ sau khi qua gate mới `INCR current_conversations`.
5. Nếu counter mới bằng max, xóa agent khỏi pool của tất cả skill.
6. Tạo reservation, owner metadata và TTL rồi trả agent.

Profile, online, offline, break, available, confirm và release cũng dùng Lua để
counter, status và toàn bộ skill pool không bị nhìn thấy ở trạng thái nửa cập nhật.
Profile update bị từ chối nếu `maxConversations < currentConversations`.

Release xử lý cả reservation chưa confirm và assignment đã confirm. Nó chỉ giảm
counter khi owner đúng; sau đó transition break hoặc phục hồi toàn bộ skill pool
nếu agent online/available và còn capacity.

## 5. API

Tạo profile rồi đưa agent online:

```bash
curl -X PUT http://localhost:8080/agents/101/profile \
  -H 'Content-Type: application/json' \
  -d '{
    "maxConversations": 3,
    "skills": ["support", "billing"],
    "teams": ["team-a"],
    "channels": ["webchat"]
  }'

curl -X POST http://localhost:8080/agents/101/online
```

Các endpoint state trả:

```json
{
  "agentId": 101,
  "state": "online",
  "status": "available",
  "currentConversations": 0,
  "maxConversations": 3
}
```

Reserve, confirm và release:

```bash
CONVERSATION_ID=8401f632-fcef-43a8-8880-eaa79a15981f

curl -X POST http://localhost:8080/agents/reserve \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CONVERSATION_ID\",\"skill\":\"support\"}"

curl -X POST \
  http://localhost:8080/reservations/$CONVERSATION_ID/confirm

curl -X POST http://localhost:8080/agents/101/release \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CONVERSATION_ID\"}"
```

State transitions bổ sung:

```text
POST /agents/{agentId}/offline
POST /agents/{agentId}/break
POST /agents/{agentId}/available
```

Agent chưa có profile trả `404`. Transition không hợp lệ, capacity update thấp
hơn current, reservation hết hạn hoặc không có agent phù hợp trả
`409 application/problem+json`. Payload/path không hợp lệ trả
`400 application/problem+json`.

## 6. Reservation TTL và giới hạn recovery

Khi `reservation:{conversationId}` hết hạn, Redis phát expired event. Listener đọc
`reservation_owner`, gọi cùng release Lua và phục hồi slot/pool. Expiry của Redis
không nhất thiết xảy ra đúng mili-giây TTL, nên recovery có thể trễ ngắn.

Keyspace notification là Pub/Sub, không có replay. Nếu Agent Service ngừng đúng
lúc key hết hạn, event có thể mất và counter bị treo. Đây là giới hạn được chấp
nhận trong Phase 3; production cần expiry index + reconciliation scheduler hoặc
đối soát counter từ Conversation Service.

## 7. Cấu hình và triển khai Minikube

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Cổng Agent Service |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_TIMEOUT` | `2s` | Command timeout |
| `RESERVATION_TTL` | `30s` | Thời hạn giữ chỗ |
| `OPENAPI_SERVER_URL` | `http://localhost:8080` | Server URL trong OpenAPI |

```bash
cd agent-service
mvn clean package
docker build -t agent-service:phase3-redis .
minikube image load --overwrite=true agent-service:phase3-redis

cd ../api-gateway
mvn clean package
docker build -t gateway-service:phase3-agent .
minikube image load --overwrite=true gateway-service:phase3-agent

cd ..
kubectl apply -f infra/agent-deployment.yaml
kubectl apply -f infra/gateway-deployment.yaml
kubectl rollout status deployment/agent-redis --timeout=180s
kubectl rollout status deployment/agent-service --timeout=180s
kubectl rollout restart deployment/spring-cloud-gateway
```

Swagger aggregate tại `http://$(minikube ip)/swagger-ui.html`; OpenAPI trực tiếp
qua Gateway tại `/api/agent/v3/api-docs`.

## 8. Smoke test và troubleshooting

```bash
kubectl exec deployment/agent-redis -- redis-cli MGET \
  agent:101:state agent:101:status \
  agent:101:current_conversations agent:101:max_conversations

kubectl exec deployment/agent-redis -- redis-cli SMEMBERS \
  available_agents:support

kubectl exec deployment/agent-redis -- redis-cli CONFIG GET \
  notify-keyspace-events

kubectl logs deployment/agent-service -f
kubectl port-forward deployment/agent-service 8084:8084
curl http://localhost:8084/actuator/health/readiness
```

Nếu reserve luôn trả 409, kiểm tra lần lượt pool, `state`, `status`, current/max và
skill profile. Không sửa counter độc lập ngoài Lua; pool có thể rebuild từ profile
nhưng counter là capacity gate cuối cùng.

Chạy test:

```bash
cd agent-service
mvn test
```

Integration test dùng Redis thật qua Testcontainers và bật expired events cho
container test.
