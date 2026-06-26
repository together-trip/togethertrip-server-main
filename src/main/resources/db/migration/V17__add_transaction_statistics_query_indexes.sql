CREATE INDEX IF NOT EXISTS idx_posts_trip_transaction_summary_active
    ON posts (trip_id, transaction_id, id)
    INCLUDE (occurred_at, category)
    WHERE deleted_at IS NULL
      AND transaction_id IS NOT NULL;
