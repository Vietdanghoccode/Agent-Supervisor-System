# User Service Design

User-service is the source of truth for accounts, public user profiles, invitations, and Supervisor
management assignments. Its `/api/user/internal/**` endpoints are service-to-service APIs.

## Data model

- `users`: unique normalized email, BCrypt password hash, role ID and account status.
- `user_profiles`: `userId` and `displayName`.
- `invites`: email, role, SHA-256 token hash, creator, status, expiry, consumed timestamp.
- `invite_teams` / `invite_permissions`: assignments selected by the inviter.
- `supervisor_assignments`: accepted Supervisor teams and permission codes.

Role IDs remain Customer `1`, Agent `2`, Supervisor `3`. New accounts are always `ACTIVE` after a
successful provisioning operation.

## Internal provisioning API

- `POST /api/user/internal/customers`: create Customer account and profile.
- `POST /api/user/internal/invites`: persist a pending invite.
- `PUT /api/user/internal/invites/{id}/resend`: rotate token hash and expiry.
- `POST /api/user/internal/invites/claim`: lock and claim an unexpired token for single-use acceptance.
- `POST /api/user/internal/invites/{id}/provision`: create account/profile and Supervisor assignment.
- `PUT /api/user/internal/invites/{id}/release`: reopen a claim when validation/provisioning fails.
- `DELETE /api/user/internal/invites/{inviteId}/users/{userId}`: compensate failed Agent provisioning.
- `GET /api/user/internal/users/by-email`: authentication lookup.

Claiming uses a pessimistic database lock, so simultaneous acceptance attempts cannot both succeed.
Unique email and token-hash constraints provide the final database-level guard.
