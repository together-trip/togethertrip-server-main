ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_transaction_type_check,
    ADD CONSTRAINT transactions_transaction_type_check
        CHECK (transaction_type IN ('EXPENSE', 'FUND_CHARGE', 'FUND_USE'));

CREATE INDEX IF NOT EXISTS idx_posts_transaction
    ON posts (transaction_id)
    WHERE transaction_id IS NOT NULL
      AND deleted_at IS NULL;
