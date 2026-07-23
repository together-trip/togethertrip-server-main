CREATE INDEX IF NOT EXISTS idx_transactions_read_list_active
    ON transactions (trip_id, status, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_fund_balance_active
    ON transactions (trip_id, status, transaction_type)
    INCLUDE (base_amount, base_currency)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_posts_transaction_summary_active
    ON posts (transaction_id, id)
    INCLUDE (occurred_at, category)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_balance_summaries_trip_projection_active
    ON trip_participant_balance_summaries (trip_id, projection_version)
    INCLUDE (trip_participant_id, paid_base_amount, share_base_amount, net_base_amount)
    WHERE deleted_at IS NULL;
