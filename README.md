# Agent-Supervisor-System

Hệ thống giám sát và phân phối conversation cho agent chăm sóc khách hàng.

Các service hiện có:

- API Gateway — routing và Swagger aggregation.
- Auth/User Service — xác thực và thông tin người dùng.
- Conversation Service — conversation lifecycle và transactional outbox.
- Agent Service — presence, break state machine và atomic capacity reservation trên Redis.
- Email Service — gửi link mời Agent/Supervisor qua SMTP.

## Signup và invite

- Customer tự đăng ký; role luôn là `CUSTOMER` và account được tạo `ACTIVE`.
- Supervisor tạo invite cho Agent/Supervisor. Token dùng một lần, hết hạn sau 24 giờ và chỉ hash được lưu.
- Agent accept invite sẽ tạo user/profile và Redis agent profile (`userId = agentId`, presence `offline`).
- Supervisor accept invite sẽ nhận team và permission đã cấu hình trong invite.

Các biến môi trường chính: `JWT_SECRET`, `USER_SERVICE_URL`, `AGENT_SERVICE_URL`, `EMAIL_SERVICE_URL`,
`INVITE_ACCEPT_BASE_URL`, `INVITE_TTL`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
`SMTP_AUTH`, `SMTP_STARTTLS`, và `SMTP_FROM`.

Build từng service bằng `mvn clean package`; build image với Dockerfile tương ứng và deploy bằng các
manifest trong `infra/`, bao gồm `kubectl apply -f infra/email-deployment.yaml`.

### Ví dụ API

```bash
curl -X POST http://localhost:8080/api/auth/signup/customer \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"password123","displayName":"Customer"}'

curl -X POST http://localhost:8080/api/auth/invites \
  -H 'Authorization: Bearer SUPERVISOR_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"email":"agent@example.com","role":"AGENT","teams":["support"],"permissions":[]}'

curl -X POST http://localhost:8080/api/auth/invites/accept \
  -H 'Content-Type: application/json' \
  -d '{"token":"TOKEN_FROM_EMAIL","password":"password123","displayName":"Agent","maxConversations":3,"skills":["support"],"channels":["webchat"]}'

curl -X POST http://localhost:8080/api/auth/invites \
  -H 'Authorization: Bearer SUPERVISOR_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"email":"lead@example.com","role":"SUPERVISOR","teams":["support"],"permissions":["MANAGE_AGENT"]}'
```

Tài liệu triển khai gần nhất:

- [Conversation Service giai đoạn 1](docs/conversation/phase-1-conversation-service.md)
- [Conversation Service giai đoạn 5 — Messaging](docs/conversation/phase-5-messaging.md)
- [Outbox Publisher giai đoạn 2](docs/conversation/phase-2-outbox-publisher.md)
- [Conversation Assignment Worker giai đoạn 3](docs/conversation/phase-3-assignment-worker.md)
- [Agent Service + Redis giai đoạn 3](docs/agent/phase-3-agent-service-redis.md)
- [Auth signup và invite](docs/auth/auth-service-design.md)
- [Agent profile provisioning](docs/agent/agent-profile-provisioning.md)
- [Email Service](docs/email/email-service-design.md)
