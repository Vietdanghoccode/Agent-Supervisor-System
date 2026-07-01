CREATE INDEX idx_conversations_assignment_waiting
    ON conversations (created_at, id)
    WHERE status IN ('WAITING', 'QUEUED');

CREATE INDEX idx_conversations_assignment_stale
    ON conversations (updated_at, id)
    WHERE status = 'ASSIGNING';
