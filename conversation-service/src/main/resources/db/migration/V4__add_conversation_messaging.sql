ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS sender_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(50) NOT NULL DEFAULT 'text/plain',
    ADD COLUMN IF NOT EXISTS message_seq BIGINT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'SENT',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS last_message_seq BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ;

WITH ranked AS (
    SELECT m.id,
           ROW_NUMBER() OVER (PARTITION BY m.conversation_id ORDER BY m.created_at, m.id) AS seq,
           c.customer_id::text AS customer_id
    FROM messages m
    JOIN conversations c ON c.id = m.conversation_id
)
UPDATE messages m
SET message_seq = ranked.seq,
    sender_id = COALESCE(m.sender_id, ranked.customer_id),
    updated_at = m.created_at
FROM ranked
WHERE m.id = ranked.id;

UPDATE conversations c
SET last_message_seq = aggregate.last_seq,
    last_message_at = aggregate.last_at
FROM (
    SELECT conversation_id, MAX(message_seq) AS last_seq, MAX(created_at) AS last_at
    FROM messages
    GROUP BY conversation_id
) aggregate
WHERE c.id = aggregate.conversation_id;

ALTER TABLE messages
    ALTER COLUMN sender_id SET NOT NULL,
    ALTER COLUMN message_seq SET NOT NULL;

DROP INDEX IF EXISTS idx_messages_conversation_created;

CREATE INDEX IF NOT EXISTS idx_messages_conversation_seq
    ON messages (conversation_id, message_seq);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_sender_client_id
    ON messages (conversation_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_conversations_agent_inbox
    ON conversations (agent_id, last_message_at DESC, id DESC)
    WHERE status = 'ASSIGNED';
