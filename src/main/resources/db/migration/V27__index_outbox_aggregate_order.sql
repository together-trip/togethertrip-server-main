CREATE INDEX IF NOT EXISTS idx_outbox_events_unpublished_aggregate_order
    ON outbox_events (aggregate_type, aggregate_id, created_at, id)
    WHERE status <> 'PUBLISHED';
