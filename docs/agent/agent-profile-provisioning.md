# Agent profile provisioning

An accepted Agent account uses `userId` as `agentId`. Auth-service creates its operational profile with:

```http
PUT /agents/{agentId}/profile
Content-Type: application/json

{
  "maxConversations": 3,
  "skills": ["support"],
  "teams": ["team-a"],
  "channels": ["webchat"]
}
```

`maxConversations` must be at least 1, `skills` must be non-empty, and all collections must be present.
Skills/channels come from the Agent acceptance request; teams come from the Supervisor-created invite.

On first creation Redis stores `state=offline`, `status=available`, and `currentConversations=0`.
`state` is presence; `status` is work availability once online. Therefore an Agent can be operationally
offline while retaining the default availability status. It receives no work until `/agents/{id}/online`.
