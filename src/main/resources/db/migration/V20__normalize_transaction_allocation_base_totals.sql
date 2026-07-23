WITH payment_totals AS (
    SELECT
        transaction_payment.transaction_id,
        max(transaction_payment.id) AS last_payment_id,
        transaction.base_amount - sum(transaction_payment.base_amount) AS adjustment
    FROM transaction_payments transaction_payment
    JOIN transactions transaction
        ON transaction.id = transaction_payment.transaction_id
    WHERE transaction_payment.deleted_at IS NULL
      AND transaction.deleted_at IS NULL
      AND transaction.status = 'ACTIVE'
    GROUP BY transaction_payment.transaction_id, transaction.base_amount
    HAVING transaction.base_amount <> sum(transaction_payment.base_amount)
)
UPDATE transaction_payments transaction_payment
SET base_amount = round((transaction_payment.base_amount + payment_totals.adjustment)::numeric, 2),
    updated_at = now()
FROM payment_totals
WHERE transaction_payment.id = payment_totals.last_payment_id;

WITH share_totals AS (
    SELECT
        transaction_share.transaction_id,
        max(transaction_share.id) AS last_share_id,
        transaction.base_amount - sum(transaction_share.base_share_amount) AS adjustment
    FROM transaction_shares transaction_share
    JOIN transactions transaction
        ON transaction.id = transaction_share.transaction_id
    WHERE transaction_share.deleted_at IS NULL
      AND transaction.deleted_at IS NULL
      AND transaction.status = 'ACTIVE'
    GROUP BY transaction_share.transaction_id, transaction.base_amount
    HAVING transaction.base_amount <> sum(transaction_share.base_share_amount)
)
UPDATE transaction_shares transaction_share
SET base_share_amount = round((transaction_share.base_share_amount + share_totals.adjustment)::numeric, 2),
    updated_at = now()
FROM share_totals
WHERE transaction_share.id = share_totals.last_share_id;
