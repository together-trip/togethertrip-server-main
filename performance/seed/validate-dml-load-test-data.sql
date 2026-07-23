-- Validate TogetherTrip DML load-test data.
--
-- This script validates trips created by performance/k6/main-dml-flow.js:
--   trip title prefix: DML_LOADTEST_
--   concurrent projection trip title prefix: DML_CONCURRENT_

\set ON_ERROR_STOP on

DO $$
DECLARE
    trip_count bigint;
    concurrent_trip_count bigint;
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
            trip.expense_version AS expense_version,
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
        GROUP BY trip.id, trip.expense_version
    )
    SELECT count(*)
    INTO mismatch_count
    FROM per_trip
    WHERE expense_version <> 2
       OR participant_count <> 3
       OR transaction_count <> 1
       OR transaction_base_amount <> 33000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid expense version or participant/transaction counts.', mismatch_count;
    END IF;

    WITH event_totals AS (
        SELECT
            trip.id AS trip_id,
            count(event.id) AS event_count,
            count(*) FILTER (WHERE event.event_type = 'CREATED') AS created_event_count,
            count(*) FILTER (WHERE event.event_type = 'UPDATED') AS updated_event_count,
            min(event.aggregate_version) AS min_aggregate_version,
            max(event.aggregate_version) AS max_aggregate_version
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_events event
          ON event.transaction_id = tx.id
         AND event.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM event_totals
    WHERE event_count <> 2
       OR created_event_count <> 1
       OR updated_event_count <> 1
       OR min_aggregate_version <> 1
       OR max_aggregate_version <> 2;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid transaction event history.', mismatch_count;
    END IF;

    WITH payment_totals AS (
        SELECT
            trip.id AS trip_id,
            count(payment.id) FILTER (WHERE payment.deleted_at IS NULL) AS active_payment_count,
            coalesce(sum(payment.base_amount) FILTER (WHERE payment.deleted_at IS NULL), 0) AS active_payment_base_amount,
            count(payment.id) FILTER (WHERE payment.deleted_at IS NOT NULL) AS deleted_payment_count,
            coalesce(sum(payment.base_amount) FILTER (WHERE payment.deleted_at IS NOT NULL), 0) AS deleted_payment_base_amount
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_payments payment
          ON payment.transaction_id = tx.id
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM payment_totals
    WHERE active_payment_count <> 1
       OR active_payment_base_amount <> 33000.00
       OR deleted_payment_count <> 1
       OR deleted_payment_base_amount <> 30000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid active/deleted payment totals.', mismatch_count;
    END IF;

    WITH post_totals AS (
        SELECT
            trip.id AS trip_id,
            count(post.id) AS post_count,
            count(*) FILTER (WHERE post.post_type = 'EXPENSE') AS expense_post_count,
            count(DISTINCT post.transaction_id) AS linked_transaction_count,
            count(*) FILTER (
                WHERE post.transaction_id IS NOT NULL
                  AND post.category = tx.category
                  AND post.occurred_at = tx.occurred_at
            ) AS synced_metadata_count
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN posts post
          ON post.transaction_id = tx.id
         AND post.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM post_totals
    WHERE post_count <> 1
       OR expense_post_count <> 1
       OR linked_transaction_count <> 1
       OR synced_metadata_count <> 1;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid expense post linkage.', mismatch_count;
    END IF;

    WITH share_totals AS (
        SELECT
            trip.id AS trip_id,
            count(share.id) FILTER (WHERE share.deleted_at IS NULL) AS active_share_count,
            coalesce(sum(share.base_share_amount) FILTER (WHERE share.deleted_at IS NULL), 0) AS active_share_base_amount,
            count(share.id) FILTER (WHERE share.deleted_at IS NOT NULL) AS deleted_share_count,
            coalesce(sum(share.base_share_amount) FILTER (WHERE share.deleted_at IS NOT NULL), 0) AS deleted_share_base_amount
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_shares share
          ON share.transaction_id = tx.id
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM share_totals
    WHERE active_share_count <> 3
       OR active_share_base_amount <> 33000.00
       OR deleted_share_count <> 3
       OR deleted_share_base_amount <> 30000.00;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid active/deleted share totals.', mismatch_count;
    END IF;

    WITH summary_totals AS (
        SELECT
            trip.id AS trip_id,
            count(summary.id) AS summary_count
        FROM trips trip
        LEFT JOIN trip_participant_balance_summaries summary
          ON summary.trip_id = trip.id
         AND summary.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM summary_totals
    WHERE summary_count <> 3;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML trips with invalid balance summary counts.', mismatch_count;
    END IF;

    WITH active_participants AS (
        SELECT
            trip.id AS trip_id,
            participant.id AS trip_participant_id
        FROM trips trip
        JOIN trip_participants participant
          ON participant.trip_id = trip.id
         AND participant.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND trip.deleted_at IS NULL
    ),
    payment_by_participant AS (
        SELECT
            tx.trip_id,
            payment.trip_participant_id,
            coalesce(sum(payment.base_amount), 0) AS paid_base_amount
        FROM transactions tx
        JOIN transaction_payments payment
          ON payment.transaction_id = tx.id
         AND payment.deleted_at IS NULL
        JOIN trips trip
          ON trip.id = tx.trip_id
         AND trip.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND tx.deleted_at IS NULL
        GROUP BY tx.trip_id, payment.trip_participant_id
    ),
    share_by_participant AS (
        SELECT
            tx.trip_id,
            share.trip_participant_id,
            coalesce(sum(share.base_share_amount), 0) AS share_base_amount
        FROM transactions tx
        JOIN transaction_shares share
          ON share.transaction_id = tx.id
         AND share.deleted_at IS NULL
        JOIN trips trip
          ON trip.id = tx.trip_id
         AND trip.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_LOADTEST_%'
          AND tx.deleted_at IS NULL
        GROUP BY tx.trip_id, share.trip_participant_id
    ),
    expected_summaries AS (
        SELECT
            participant.trip_id,
            participant.trip_participant_id,
            coalesce(payment.paid_base_amount, 0) AS paid_base_amount,
            coalesce(share.share_base_amount, 0) AS share_base_amount,
            coalesce(payment.paid_base_amount, 0) - coalesce(share.share_base_amount, 0) AS net_base_amount
        FROM active_participants participant
        LEFT JOIN payment_by_participant payment
          ON payment.trip_id = participant.trip_id
         AND payment.trip_participant_id = participant.trip_participant_id
        LEFT JOIN share_by_participant share
          ON share.trip_id = participant.trip_id
         AND share.trip_participant_id = participant.trip_participant_id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM expected_summaries expected
    FULL OUTER JOIN trip_participant_balance_summaries summary
      ON summary.trip_id = expected.trip_id
     AND summary.trip_participant_id = expected.trip_participant_id
     AND summary.deleted_at IS NULL
    JOIN trips trip
      ON trip.id = coalesce(expected.trip_id, summary.trip_id)
     AND trip.deleted_at IS NULL
    WHERE trip.title LIKE 'DML_LOADTEST_%'
      AND (
          expected.trip_participant_id IS NULL
       OR summary.trip_participant_id IS NULL
       OR summary.paid_base_amount <> expected.paid_base_amount
       OR summary.share_base_amount <> expected.share_base_amount
       OR summary.net_base_amount <> expected.net_base_amount
       OR summary.projection_version <> trip.expense_version
      );

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML balance summaries that do not match active transaction allocations.', mismatch_count;
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

    SELECT count(*)
    INTO concurrent_trip_count
    FROM trips trip
    WHERE trip.title LIKE 'DML_CONCURRENT_%'
      AND trip.deleted_at IS NULL;

    IF concurrent_trip_count = 0 THEN
        RAISE EXCEPTION 'No DML concurrent projection trips found.';
    END IF;

    WITH per_trip AS (
        SELECT
            trip.id AS trip_id,
            trip.expense_version AS expense_version,
            count(DISTINCT participant.id) AS participant_count,
            count(DISTINCT tx.id) AS transaction_count
        FROM trips trip
        LEFT JOIN trip_participants participant
          ON participant.trip_id = trip.id
         AND participant.deleted_at IS NULL
        LEFT JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id, trip.expense_version
    )
    SELECT count(*)
    INTO mismatch_count
    FROM per_trip
    WHERE expense_version <= transaction_count
       OR participant_count <> 3
       OR transaction_count = 0;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML concurrent trips with invalid expense version or participant/transaction counts.', mismatch_count;
    END IF;

    WITH event_totals AS (
        SELECT
            trip.id AS trip_id,
            trip.expense_version AS expense_version,
            count(DISTINCT tx.id) AS transaction_count,
            count(event.id) AS event_count,
            count(*) FILTER (WHERE event.event_type = 'CREATED') AS created_event_count,
            count(*) FILTER (WHERE event.event_type = 'UPDATED') AS updated_event_count,
            count(DISTINCT tx.id) FILTER (WHERE event.event_type = 'UPDATED') AS updated_transaction_count
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_events event
          ON event.transaction_id = tx.id
         AND event.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id, trip.expense_version
    )
    SELECT count(*)
    INTO mismatch_count
    FROM event_totals
    WHERE event_count <> expense_version
       OR created_event_count <> transaction_count
       OR updated_event_count < transaction_count
       OR updated_transaction_count <> transaction_count;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML concurrent trips with invalid transaction event history.', mismatch_count;
    END IF;

    WITH payment_totals AS (
        SELECT
            trip.id AS trip_id,
            count(DISTINCT tx.id) AS transaction_count,
            count(payment.id) FILTER (WHERE payment.deleted_at IS NULL) AS active_payment_count,
            coalesce(sum(payment.base_amount) FILTER (WHERE payment.deleted_at IS NULL), 0) AS active_payment_base_amount,
            count(payment.id) FILTER (WHERE payment.deleted_at IS NOT NULL) AS deleted_payment_count
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_payments payment
          ON payment.transaction_id = tx.id
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    ),
    share_totals AS (
        SELECT
            trip.id AS trip_id,
            count(DISTINCT tx.id) AS transaction_count,
            count(share.id) FILTER (WHERE share.deleted_at IS NULL) AS active_share_count,
            coalesce(sum(share.base_share_amount) FILTER (WHERE share.deleted_at IS NULL), 0) AS active_share_base_amount,
            count(share.id) FILTER (WHERE share.deleted_at IS NOT NULL) AS deleted_share_count
        FROM trips trip
        JOIN transactions tx
          ON tx.trip_id = trip.id
         AND tx.deleted_at IS NULL
        LEFT JOIN transaction_shares share
          ON share.transaction_id = tx.id
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND trip.deleted_at IS NULL
        GROUP BY trip.id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM payment_totals payment
    JOIN share_totals share
      ON share.trip_id = payment.trip_id
    WHERE payment.active_payment_count <> payment.transaction_count
       OR payment.deleted_payment_count < payment.transaction_count
       OR share.active_share_count <> share.transaction_count * 3
       OR share.deleted_share_count < share.transaction_count * 3
       OR payment.active_payment_base_amount <> share.active_share_base_amount;

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML concurrent trips with invalid active/deleted allocation totals.', mismatch_count;
    END IF;

    WITH active_participants AS (
        SELECT
            trip.id AS trip_id,
            participant.id AS trip_participant_id
        FROM trips trip
        JOIN trip_participants participant
          ON participant.trip_id = trip.id
         AND participant.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND trip.deleted_at IS NULL
    ),
    payment_by_participant AS (
        SELECT
            tx.trip_id,
            payment.trip_participant_id,
            coalesce(sum(payment.base_amount), 0) AS paid_base_amount
        FROM transactions tx
        JOIN transaction_payments payment
          ON payment.transaction_id = tx.id
         AND payment.deleted_at IS NULL
        JOIN trips trip
          ON trip.id = tx.trip_id
         AND trip.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND tx.deleted_at IS NULL
        GROUP BY tx.trip_id, payment.trip_participant_id
    ),
    share_by_participant AS (
        SELECT
            tx.trip_id,
            share.trip_participant_id,
            coalesce(sum(share.base_share_amount), 0) AS share_base_amount
        FROM transactions tx
        JOIN transaction_shares share
          ON share.transaction_id = tx.id
         AND share.deleted_at IS NULL
        JOIN trips trip
          ON trip.id = tx.trip_id
         AND trip.deleted_at IS NULL
        WHERE trip.title LIKE 'DML_CONCURRENT_%'
          AND tx.deleted_at IS NULL
        GROUP BY tx.trip_id, share.trip_participant_id
    ),
    expected_summaries AS (
        SELECT
            participant.trip_id,
            participant.trip_participant_id,
            coalesce(payment.paid_base_amount, 0) AS paid_base_amount,
            coalesce(share.share_base_amount, 0) AS share_base_amount,
            coalesce(payment.paid_base_amount, 0) - coalesce(share.share_base_amount, 0) AS net_base_amount
        FROM active_participants participant
        LEFT JOIN payment_by_participant payment
          ON payment.trip_id = participant.trip_id
         AND payment.trip_participant_id = participant.trip_participant_id
        LEFT JOIN share_by_participant share
          ON share.trip_id = participant.trip_id
         AND share.trip_participant_id = participant.trip_participant_id
    )
    SELECT count(*)
    INTO mismatch_count
    FROM expected_summaries expected
    FULL OUTER JOIN trip_participant_balance_summaries summary
      ON summary.trip_id = expected.trip_id
     AND summary.trip_participant_id = expected.trip_participant_id
     AND summary.deleted_at IS NULL
    JOIN trips trip
      ON trip.id = coalesce(expected.trip_id, summary.trip_id)
     AND trip.deleted_at IS NULL
    WHERE trip.title LIKE 'DML_CONCURRENT_%'
      AND (
          expected.trip_participant_id IS NULL
       OR summary.trip_participant_id IS NULL
       OR summary.paid_base_amount <> expected.paid_base_amount
       OR summary.share_base_amount <> expected.share_base_amount
       OR summary.net_base_amount <> expected.net_base_amount
       OR summary.projection_version <> trip.expense_version
      );

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'Found % DML concurrent balance summaries that do not match active transaction allocations.', mismatch_count;
    END IF;

    RAISE NOTICE 'DML load-test data validation passed for % trips.', trip_count;
    RAISE NOTICE 'DML concurrent projection validation passed for % trips.', concurrent_trip_count;
END $$;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
      AND deleted_at IS NULL
),
concurrent_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_CONCURRENT_%'
      AND deleted_at IS NULL
)
SELECT 'dml_trips' AS item, count(*) AS count
FROM dml_trips
UNION ALL
SELECT 'dml_concurrent_trips', count(*)
FROM concurrent_trips
UNION ALL
SELECT 'dml_participants', count(*)
FROM trip_participants participant
JOIN dml_trips trip ON trip.id = participant.trip_id
WHERE participant.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_participants', count(*)
FROM trip_participants participant
JOIN concurrent_trips trip ON trip.id = participant.trip_id
WHERE participant.deleted_at IS NULL
UNION ALL
SELECT 'dml_transactions', count(*)
FROM transactions tx
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_transactions', count(*)
FROM transactions tx
JOIN concurrent_trips trip ON trip.id = tx.trip_id
WHERE tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_posts', count(*)
FROM posts post
JOIN dml_trips trip ON trip.id = post.trip_id
WHERE post.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_posts', count(*)
FROM posts post
JOIN concurrent_trips trip ON trip.id = post.trip_id
WHERE post.deleted_at IS NULL
UNION ALL
SELECT 'dml_events', count(*)
FROM transaction_events event
JOIN transactions tx ON tx.id = event.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE event.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_events', count(*)
FROM transaction_events event
JOIN transactions tx ON tx.id = event.transaction_id
JOIN concurrent_trips trip ON trip.id = tx.trip_id
WHERE event.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_payments', count(*)
FROM transaction_payments payment
JOIN transactions tx ON tx.id = payment.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE payment.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_payments', count(*)
FROM transaction_payments payment
JOIN transactions tx ON tx.id = payment.transaction_id
JOIN concurrent_trips trip ON trip.id = tx.trip_id
WHERE payment.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_deleted_payments', count(*)
FROM transaction_payments payment
JOIN transactions tx ON tx.id = payment.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE payment.deleted_at IS NOT NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_deleted_payments', count(*)
FROM transaction_payments payment
JOIN transactions tx ON tx.id = payment.transaction_id
JOIN concurrent_trips trip ON trip.id = tx.trip_id
WHERE payment.deleted_at IS NOT NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_shares', count(*)
FROM transaction_shares share
JOIN transactions tx ON tx.id = share.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE share.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_shares', count(*)
FROM transaction_shares share
JOIN transactions tx ON tx.id = share.transaction_id
JOIN concurrent_trips trip ON trip.id = tx.trip_id
WHERE share.deleted_at IS NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_deleted_shares', count(*)
FROM transaction_shares share
JOIN transactions tx ON tx.id = share.transaction_id
JOIN dml_trips trip ON trip.id = tx.trip_id
WHERE share.deleted_at IS NOT NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_deleted_shares', count(*)
FROM transaction_shares share
JOIN transactions tx ON tx.id = share.transaction_id
JOIN concurrent_trips trip ON trip.id = tx.trip_id
WHERE share.deleted_at IS NOT NULL
  AND tx.deleted_at IS NULL
UNION ALL
SELECT 'dml_balance_summaries', count(*)
FROM trip_participant_balance_summaries summary
JOIN dml_trips trip ON trip.id = summary.trip_id
WHERE summary.deleted_at IS NULL
UNION ALL
SELECT 'dml_concurrent_balance_summaries', count(*)
FROM trip_participant_balance_summaries summary
JOIN concurrent_trips trip ON trip.id = summary.trip_id
WHERE summary.deleted_at IS NULL
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
