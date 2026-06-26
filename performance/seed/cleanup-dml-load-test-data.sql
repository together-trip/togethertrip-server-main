-- Hard-delete TogetherTrip DML load-test data.
--
-- This script deletes only trips created by performance/k6/main-dml-flow.js:
--   trip title prefix: DML_LOADTEST_

\set ON_ERROR_STOP on

INSERT INTO oauth_accounts (
    user_id,
    provider,
    provider_user_id,
    nickname,
    profile_image_url,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    user_row.id,
    'KAKAO',
    sample.provider_user_id,
    sample.oauth_nickname,
    user_row.profile_image_url,
    now(),
    now(),
    NULL
FROM (
    VALUES
        ('로컬 verified hana', 'local-test-verified:hana', '로컬 테스트 verified:hana'),
        ('로컬 verified minseo', 'local-test-verified:minseo', '로컬 테스트 verified:minseo'),
        ('로컬 verified joon', 'local-test-verified:joon', '로컬 테스트 verified:joon')
) AS sample(user_nickname, provider_user_id, oauth_nickname)
JOIN users user_row
  ON user_row.nickname = sample.user_nickname
 AND user_row.deleted_at IS NULL
ON CONFLICT (provider, provider_user_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    nickname = EXCLUDED.nickname,
    profile_image_url = EXCLUDED.profile_image_url,
    updated_at = EXCLUDED.updated_at,
    deleted_at = NULL;

BEGIN;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
),
dml_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN dml_trips trip ON trip.id = tx.trip_id
),
dml_posts AS (
    SELECT post.id
    FROM posts post
    JOIN dml_trips trip ON trip.id = post.trip_id
)
DELETE FROM post_attachments attachment
USING dml_posts post
WHERE attachment.post_id = post.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
),
dml_posts AS (
    SELECT post.id
    FROM posts post
    JOIN dml_trips trip ON trip.id = post.trip_id
)
DELETE FROM post_comments comment
USING dml_posts post
WHERE comment.post_id = post.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM settlement_transfers transfer
USING settlements settlement, dml_trips trip
WHERE transfer.settlement_id = settlement.id
  AND settlement.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM settlements settlement
USING dml_trips trip
WHERE settlement.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM trip_participant_balance_summaries summary
USING dml_trips trip
WHERE summary.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
),
dml_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN dml_trips trip ON trip.id = tx.trip_id
)
DELETE FROM transaction_events event
USING dml_transactions tx
WHERE event.transaction_id = tx.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
),
dml_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN dml_trips trip ON trip.id = tx.trip_id
)
DELETE FROM transaction_shares share
USING dml_transactions tx
WHERE share.transaction_id = tx.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
),
dml_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN dml_trips trip ON trip.id = tx.trip_id
)
DELETE FROM transaction_payments payment
USING dml_transactions tx
WHERE payment.transaction_id = tx.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM posts post
USING dml_trips trip
WHERE post.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM transactions tx
USING dml_trips trip
WHERE tx.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM trip_exchange_rates rate
USING dml_trips trip
WHERE rate.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM trip_countries country
USING dml_trips trip
WHERE country.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM trip_invitations invitation
USING dml_trips trip
WHERE invitation.trip_id = trip.id;

WITH dml_trips AS (
    SELECT id
    FROM trips
    WHERE title LIKE 'DML_LOADTEST_%'
)
DELETE FROM trip_participants participant
USING dml_trips trip
WHERE participant.trip_id = trip.id;

DELETE FROM trips
WHERE title LIKE 'DML_LOADTEST_%';

COMMIT;

ANALYZE trips;
ANALYZE trip_participants;
ANALYZE transactions;
ANALYZE transaction_payments;
ANALYZE transaction_shares;
ANALYZE settlements;
ANALYZE settlement_transfers;
