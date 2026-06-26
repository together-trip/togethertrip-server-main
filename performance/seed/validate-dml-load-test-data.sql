-- Validate TogetherTrip DML load-test data.
--
-- This script validates trips created by performance/k6/main-dml-flow.js:
--   trip title prefix: DML_LOADTEST_

\set ON_ERROR_STOP on

DO $$
DECLARE
    trip_count bigint;
    mismatch_count bigint;
BEGIN
    SELECT count(*)
    INTO trip_count
    FROM trips trip
    WHERE trip.title LIKE 'DML_LOADTEST_%'
      AND trip.deleted_at IS NULL;

    IF trip_count = 0 THEN
        RAISE EXCEPTION 'No DML load-test trips found.';
    END IF;

    SELECT count(*)
    INTO mismatch_count
    FROM trips trip
    WHERE trip.title LIKE 'DML_LOADTEST_%'
      AND trip.deleted_at IS NULL
      AND trip.settlement_status <> 'SETTLED';

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips not settled.', mismatch_count;
    END IF;

    WITH per_trip AS (
        SELECT
            trip.id AS trip_id,
            count(DISTINCT participant.id) AS participant_count,
            count(DISTINCT tx.id) AS transaction_count,
            coalesce(sum(DISTINCT tx.base_amount), 0) AS transaction_base_amount
        FROM trips trip
        LEFT JOIN trip_participants participant
          ON participant.trip_id = trip.id
         AND participant.deleted_at IS NULL
        LEFT JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM per_trip
    WHERE participant_count <> 3
       OR transaction_count <> 1
       OR transaction_base_amount <> 33000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid participant/transaction counts.', mismatch_count;
    END IF;

    WITH payment_totals AS (
        SELECT
            trip.id AS trip_id,
            count(payment.id) AS payment_count,
            coalesce(sum(payment.base_amount), 0) AS payment_base_amount
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_payments payment
          ON payment.transaction_id = tx.id
         AND payment.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM payment_totals
    WHERE payment_count <> 1
       OR payment_base_amount <> 33000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid payment totals.', mismatch_count;
    END IF;

    WITH share_totals AS (
        SELECT
            trip.id AS trip_id,
            count(share.id) AS share_count,
            coalesce(sum(share.base_share_amount), 0) AS share_base_amount
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_shares share
          ON share.transaction_id = tx.id
         AND share.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM share_totals
    WHERE share_count <> 3
       OR share_base_amount <> 33000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid share totals.', mismatch_count;
    END IF;

    WITH settlement_totals AS (
        SELECT
            trip.id AS trip_id,
            count(settlement.id) AS settlement_count,
            coalesce(sum(settlement.total_expense_amount), 0) AS total_expense_amount,
            coalesce(sum(settlement.total_share_amount), 0) AS total_share_amount,
            count(*) FILTER (WHERE settlement.status = 'CONFIRMED') AS confirmed_count
        FROM trips trip
        LEFT JOIN settlements settlement
          ON settlement.trip_id = trip.id
         AND settlement.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM settlement_totals
    WHERE settlement_count <> 1
       OR confirmed_count <> 1
       OR total_expense_amount <> 33000.00
       OR total_share_amount <> 33000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid settlement totals.', mismatch_count;
    END IF;

    WITH transfer_totals AS (
        SELECT
            trip.id AS trip_id,
            count(transfer.id) AS transfer_count,
            count(*) FILTER (WHERE transfer.status = 'COMPLETED') AS completed_count,
            count(*) FILTER (WHERE transfer.sender_confirmed_at IS NOT NULL) AS sender_confirmed_count,
            count(*) FILTER (WHERE transfer.receiver_confirmed_at IS NOT NULL) AS receiver_confirmed_count,
            count(*) FILTER (WHERE transfer.completed_at IS NOT NULL) AS completed_at_count,
            coalesce(sum(transfer.amount), 0) AS transfer_amount
        FROM trips trip
        JOIN settlements settlement
          ON settlement.trip_id = trip.id
         AND settlement.deleted_at IS NULL
        LEFT JOIN settlement_transfers transfer
          ON transfer.settlement_id = settlement.id
         AND transfer.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM transfer_totals
    WHERE transfer_count <> 2
       OR completed_count <> 2
       OR sender_confirmed_count <> 2
       OR receiver_confirmed_count <> 2
       OR completed_at_count <> 2
       OR transfer_amount <> 22000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid settlement transfers.', mismatch_count;
    END IF;

    RAISE NOTICE 'DML load-test data validation passed for % trips.', trip_count;
END $$;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
      AND deleted_at IS NULL
)
SELECT 'dml_trips' AS item, count(*) AS count
FROM dml_trips
UNION ALL
SELECT 'dml_participants', count(*)
FROM trip_participants participant
JOIN dml_trips trip ON trip.id = participant.trip_id
WHERE participant.deleted_at IS NULL
UNION ALL
SELECT 'dml_transactions', count(*)
FROM transactions tx
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_payments', count(*)
FROM transaction_payments payment
JOIN transactions tx ON tx.id = payment.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE payment.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_shares', count(*)
FROM transaction_shares share
JOIN transactions tx ON tx.id = share.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE share.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_settlements', count(*)
FROM settlements settlement
JOIN dml_trips trip ON trip.id = settlement.trip_id
WHERE settlement.deleted_at IS NULL
UNION ALL
SELECT 'dml_transfers', count(*)
FROM settlement_transfers transfer
JOIN settlements settlement ON settlement.id = transfer.settlement_id
JOIN dml_trips trip ON trip.id = settlement.trip_id
WHERE transfer.deleted_at IS NULL
  AND settlement.deleted_at IS NULL
UNION ALL
SELECT 'dml_completed_transfers', count(*)
FROM settlement_transfers transfer
JOIN settlements settlement ON settlement.id = transfer.settlement_id
JOIN dml_trips trip ON trip.id = settlement.trip_id
WHERE transfer.deleted_at IS NULL
  AND settlement.deleted_at IS NULL
  AND transfer.status = 'COMPLETED'
ORDER BY item;
