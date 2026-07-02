# Email Service Design

Email-service exposes `POST /internal/emails/invitations` for auth-service only. It sends a plain-text
invitation containing inviter, role, expiry, and `${INVITE_ACCEPT_BASE_URL}?token=...` via SMTP.

Configuration:

- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`
- `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_FROM`

For local development, point the service at Mailpit/MailHog on port 1025. SMTP errors propagate to
auth-service as `502`. The invite remains pending; `POST /api/auth/invites/{id}/resend` rotates the token
and retries delivery. Do not log request bodies because they contain the raw invitation URL.

Kubernetes deployment expects an `smtp-secret` with `username` and `password` keys, for example:
`kubectl create secret generic smtp-secret --from-literal=username=... --from-literal=password=...`.
