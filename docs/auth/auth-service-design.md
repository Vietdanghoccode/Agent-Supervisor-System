# Auth Service: signup and invitation

Auth-service issues JWTs and orchestrates account provisioning. Credentials remain in user-service;
auth-service hashes passwords with BCrypt and never persists raw passwords or invitation tokens.

## Public API

### `POST /api/auth/signup/customer`

```json
{"email":"customer@example.com","password":"password123","displayName":"Customer"}
```

The server always creates role `CUSTOMER` and status `ACTIVE`. A successful response is `201` with
`accessToken` and `refreshToken`. Duplicate email is `409`; validation errors are `400`.

### `POST /api/auth/invites`

Requires a valid Supervisor bearer token.

```json
{
  "email":"agent@example.com",
  "role":"AGENT",
  "teams":["support"],
  "permissions":[]
}
```

For `SUPERVISOR`, `permissions` contains the management permission codes assigned on acceptance.
The response is `201` with invite metadata. The random token is SHA-256 hashed before persistence;
the raw value exists only in the frontend link sent by email. Invites expire after 24 hours.

### `POST /api/auth/invites/{inviteId}/resend`

Requires Supervisor. It rotates the token, invalidates the previous link, resets the 24-hour expiry,
and sends a new email. Only `PENDING` invites can be resent.

### `POST /api/auth/invites/accept`

Agent example:

```json
{
  "token":"raw-token-from-email",
  "password":"password123",
  "displayName":"Support Agent",
  "maxConversations":3,
  "skills":["support"],
  "channels":["webchat"]
}
```

Teams are intentionally absent: they come from the invite. Supervisor acceptance uses the common
`token`, `password`, and `displayName` fields; its teams and permissions also come from the invite.
A successful response contains access and refresh tokens. Invalid tokens return `404`, and expired or
consumed tokens return `410`.

## Provisioning sequences

Customer: client -> auth -> user-service account/profile -> JWT response.

Agent: Supervisor -> auth invite -> user-service hashed invite -> email-service -> Agent accepts ->
user-service account/profile -> agent-service profile -> finalize response. The account ID is the agent
ID. If agent-service fails, user-service removes the new account/profile and reopens the invite.

Supervisor: Supervisor -> auth invite -> email-service -> acceptance -> user-service atomically creates
account/profile and team/permission assignment -> JWT response.

All successfully provisioned accounts are `ACTIVE`. Email delivery failure is reported as `502`; the
pending invite can be recovered with resend.
