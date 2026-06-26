-- Validate TogetherTrip load-test seed data.
--
-- This script fails with an exception when the k6 seed data is missing,
-- duplicated, or internally inconsistent.

\set ON_ERROR_STOP on

DO $$
DECLARE
    load_trip_id bigint;
    actual_count bigint;
    mismatch_count bigint;
BEGIN
    SELECT trip.id
    INTO load_trip_id
    FROM trips trip
    WHERE trip.title = 'LOADTEST_정산_대량_여행'
      AND trip.deleted_at IS NULL;

    IF load_trip_id IS NULL THEN
        RAISE EXCEPTION 'Load-test trip was not found.';
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM trips trip
    WHERE trip.title = 'LOADTEST_정산_대량_여행'
      AND trip.deleted_at IS NULL;

    IF actual_count <> 1 THEN
        RAISE EXCEPTION 'Expected 1 active load-test trip, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM users user_row
    WHERE user_row.nickname LIKE 'LOADTEST_USER_%'
      AND user_row.deleted_at IS NULL;

    IF actual_count <> 9 THEN
        RAISE EXCEPTION 'Expected 9 generated users, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM oauth_accounts account
    JOIN users user_row ON user_row.id = account.user_id
    WHERE account.provider = 'KAKAO'
      AND account.provider_user_id = 'local-test-verified:hana'
      AND account.deleted_at IS NULL
      AND user_row.nickname = '로컬 verified hana'
      AND user_row.deleted_at IS NULL;

    IF actual_count <> 1 THEN
        RAISE EXCEPTION 'Expected 1 local test oauth account for hana, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM trip_participants participant
    WHERE participant.trip_id = load_trip_id
      AND participant.deleted_at IS NULL;

    IF actual_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 active participants, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM transactions tx
    WHERE tx.trip_id = load_trip_id
      AND tx.deleted_at IS NULL;

    IF actual_count <> 100000 THEN
        RAISE EXCEPTION 'Expected 100000 active transactions, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM transaction_payments payment
    JOIN transactions tx ON tx.id = payment.transaction_id
    WHERE tx.trip_id = load_trip_id
      AND tx.deleted_at IS NULL
      AND payment.deleted_at IS NULL;

    IF actual_count <> 100000 THEN
        RAISE EXCEPTION 'Expected 100000 active payments, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM transaction_shares share
    JOIN transactions tx ON tx.id = share.transaction_id
    WHERE tx.trip_id = load_trip_id
      AND tx.deleted_at IS NULL
      AND share.deleted_at IS NULL;

    IF actual_count <> 500000 THEN
        RAISE EXCEPTION 'Expected 500000 active shares, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO actual_count
    FROM trip_participant_balance_summaries summary
    WHERE summary.trip_id = load_trip_id;

    IF actual_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 balance summaries, got %.', actual_count;
    END IF;

    SELECT count(*)
    INTO mismatch_count
    FROM transaction_payments payment
    JOIN transactions tx
      ON tx.id = payment.transaction_id
     AND tx.trip_id = load_trip_id
     AND tx.deleted_at IS NULL
    LEFT JOIN trip_participants participant
      ON participant.id = payment.trip_participant_id
     AND participant.trip_id = load_trip_id
     AND participant.deleted_at IS NULL
    WHERE payment.deleted_at IS NULL
      AND participant.id IS NULL;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % orphan or cross-trip payment rows.', mismatch_count;
    END IF;

    SELECT count(*)
    INTO mismatch_count
    FROM transaction_shares share
    JOIN transactions tx
      ON tx.id = share.transaction_id
     AND tx.trip_id = load_trip_id
     AND tx.deleted_at IS NULL
    LEFT JOIN trip_participants participant
      ON participant.id = share.trip_participant_id
     AND participant.trip_id = load_trip_id
     AND participant.deleted_at IS NULL
    WHERE share.deleted_at IS NULL
      AND participant.id IS NULL;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % orphan or cross-trip share rows.', mismatch_count;
    END IF;

    WITH payment_totals AS (
        SELECT
            tx.id AS transaction_id,
            tx.base_amount AS transaction_base_amount,
            count(payment.id) AS payment_count,
            coalesce(sum(payment.base_amount), 0) AS payment_base_amount
        FROM transactions tx
        LEFT JOIN transaction_payments payment
          ON payment.transaction_id = tx.id
         AND payment.deleted_at IS NULL
        WHERE tx.trip_id = load_trip_id
          AND tx.deleted_at IS NULL
        GROUP BY tx.id, tx.base_amount
    )
    SELECT count(*)
    INTO mismatch_count
    FROM payment_totals total
    WHERE total.payment_count <> 1
       OR total.payment_base_amount <> total.transaction_base_amount;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % transactions with invalid payment totals.', mismatch_count;
    END IF;

    WITH share_totals AS (
        SELECT
            tx.id AS transaction_id,
            tx.base_amount AS transaction_base_amount,
            count(share.id) AS share_count,
            coalesce(sum(share.base_share_amount), 0) AS share_base_amount
        FROM transactions tx
        LEFT JOIN transaction_shares share
          ON share.transaction_id = tx.id
         AND share.deleted_at IS NULL
        WHERE tx.trip_id = load_trip_id
          AND tx.deleted_at IS NULL
        GROUP BY tx.id, tx.base_amount
    )
    SELECT count(*)
    INTO mismatch_count
    FROM share_totals total
    WHERE total.share_count <> 5
       OR total.share_base_amount <> total.transaction_base_amount;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % transactions with invalid share totals.', mismatch_count;
    END IF;

    WITH payment_totals AS (
        SELECT
            payment.trip_participant_id,
            sum(payment.base_amount) AS paid_base_amount
        FROM transaction_payments payment
        JOIN transactions tx ON tx.id = payment.transaction_id
        WHERE tx.trip_id = load_trip_id
          AND tx.deleted_at IS NULL
          AND payment.deleted_at IS NULL
        GROUP BY payment.trip_participant_id
    ),
    share_totals AS (
        SELECT
            share.trip_participant_id,
            sum(share.base_share_amount) AS share_base_amount
        FROM transaction_shares share
        JOIN transactions tx ON tx.id = share.transaction_id
        WHERE tx.trip_id = load_trip_id
          AND tx.deleted_at IS NULL
          AND share.deleted_at IS NULL
        GROUP BY share.trip_participant_id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM trip_participant_balance_summaries summary
    JOIN trip_participants participant
      ON participant.id = summary.trip_participant_id
     AND participant.trip_id = load_trip_id
     AND participant.deleted_at IS NULL
    LEFT JOIN payment_totals payment
      ON payment.trip_participant_id = participant.id
    LEFT JOIN share_totals share
      ON share.trip_participant_id = participant.id
    WHERE summary.trip_id = load_trip_id
      AND (
          summary.paid_base_amount <> coalesce(payment.paid_base_amount, 0)
       OR summary.share_base_amount <> coalesce(share.share_base_amount, 0)
       OR summary.net_base_amount <> coalesce(payment.paid_base_amount, 0) - coalesce(share.share_base_amount, 0)
       OR summary.projection_version <> 100000
      );

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % invalid balance summary rows.', mismatch_count;
    END IF;

    RAISE NOTICE 'Load-test data validation passed for trip id %.', load_trip_id;
END $$;

WITH loadtest_trip AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
      AND deleted_at IS NULL
)
SELECT 'trips' AS item, count(*) AS count
FROM loadtest_trip
UNION ALL
SELECT 'generated_users', count(*)
FROM users
WHERE nickname LIKE 'LOADTEST_USER_%'
  AND deleted_at IS NULL
UNION ALL
SELECT 'participants', count(*)
FROM trip_participants participant
JOIN loadtest_trip trip ON trip.id = participant.trip_id
WHERE participant.deleted_at IS NULL
UNION ALL
SELECT 'transactions', count(*)
FROM transactions tx
JOIN loadtest_trip trip ON trip.id = tx.trip_id
WHERE tx.deleted_at IS NULL
UNION ALL
SELECT 'payments', count(*)
FROM transaction_payments payment
JOIN transactions tx ON tx.id = payment.transaction_id
JOIN loadtest_trip trip ON trip.id = tx.trip_id
WHERE tx.deleted_at IS NULL
  AND payment.deleted_at IS NULL
UNION ALL
SELECT 'shares', count(*)
FROM transaction_shares share
JOIN transactions tx ON tx.id = share.transaction_id
JOIN loadtest_trip trip ON trip.id = tx.trip_id
WHERE tx.deleted_at IS NULL
  AND share.deleted_at IS NULL
UNION ALL
SELECT 'balance_summaries', count(*)
FROM trip_participant_balance_summaries summary
JOIN loadtest_trip trip ON trip.id = summary.trip_id
ORDER BY item;
