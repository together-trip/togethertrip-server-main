ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS category VARCHAR(30),
    ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;

UPDATE transactions tx
SET category = first_post.category,
    occurred_at = first_post.occurred_at
FROM (
    SELECT DISTINCT ON (post.transaction_id)
           post.transaction_id,
           post.category,
           post.occurred_at
    FROM posts post
    WHERE post.deleted_at IS NULL
      AND post.transaction_id IS NOT NULL
    ORDER BY post.transaction_id, post.id
) first_post
WHERE tx.id = first_post.transaction_id
  AND tx.deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_statistics_period_active
    ON transactions (trip_id, status, occurred_at)
    INCLUDE (transaction_type, base_amount)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_category_statistics_active
    ON transactions (trip_id, status, category, occurred_at)
    INCLUDE (base_amount)
    WHERE deleted_at IS NULL;

ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS chk_posts_type_transaction_consistency;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_type_transaction_consistency
        CHECK (
            (post_type = 'RECORD' AND transaction_id IS NULL)
            OR (post_type = 'EXPENSE' AND transaction_id IS NOT NULL)
        );

CREATE UNIQUE INDEX IF NOT EXISTS ux_posts_active_expense_transaction
    ON posts (transaction_id)
    WHERE deleted_at IS NULL
      AND post_type = 'EXPENSE'
      AND transaction_id IS NOT NULL;
