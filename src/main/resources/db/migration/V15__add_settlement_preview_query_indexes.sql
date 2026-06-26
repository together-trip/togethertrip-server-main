CREATE INDEX IF NOT EXISTS idx_transactions_settlement_preview_active
    ON transactions (trip_id, status, id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_payments_settlement_preview_active
    ON transaction_payments (transaction_id, trip_participant_id)
    INCLUDE (base_amount)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_shares_settlement_preview_active
    ON transaction_shares (transaction_id, trip_participant_id)
    INCLUDE (base_share_amount)
    WHERE deleted_at IS NULL;
