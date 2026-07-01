# Agent-Supervisor-System

Hệ thống giám sát và phân phối conversation cho agent chăm sóc khách hàng.

Các service hiện có:

- API Gateway — routing và Swagger aggregation.
- Auth/User Service — xác thực và thông tin người dùng.
- Conversation Service — conversation lifecycle và transactional outbox.
- Agent Service — presence, break state machine và atomic capacity reservation trên Redis.

Tài liệu triển khai gần nhất:

- [Conversation Service giai đoạn 1](docs/conversation/phase-1-conversation-service.md)
- [Outbox Publisher giai đoạn 2](docs/conversation/phase-2-outbox-publisher.md)
- [Conversation Assignment Worker giai đoạn 3](docs/conversation/phase-3-assignment-worker.md)
- [Agent Service + Redis giai đoạn 3](docs/agent/phase-3-agent-service-redis.md)
