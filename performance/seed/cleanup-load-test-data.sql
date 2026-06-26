-- Hard-delete TogetherTrip load-test seed data.
--
-- This script deletes only data created for k6 load testing:
--   trip title: LOADTEST_정산_대량_여행
--   generated users: LOADTEST_USER_%
--
-- Run from togethertrip-server-main while gateway docker compose database is up:
--   psql "postgresql://together_trip:together_trip@localhost:5432/together_trip" \
--     -f performance/seed/cleanup-load-test-data.sql

-- Full FK indexes make repeated hard-delete cleanup fast.
-- The application migrations already have several partial indexes, but PostgreSQL
-- FK checks during parent deletes cannot always use partial indexes.
CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_transaction_payments_transaction
    ON transaction_payments (transaction_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_transaction_shares_transaction
    ON transaction_shares (transaction_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_transaction_events_transaction
    ON transaction_events (transaction_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_posts_transaction
    ON posts (transaction_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_posts_trip
    ON posts (trip_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_post_attachments_post
    ON post_attachments (post_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_post_comments_post
    ON post_comments (post_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_settlements_trip
    ON settlements (trip_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_trip_participants_trip
    ON trip_participants (trip_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_trip_countries_trip
    ON trip_countries (trip_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_trip_exchange_rates_trip
    ON trip_exchange_rates (trip_id);

CREATE INDEX IF NOT EXISTS idx_loadtest_cleanup_trip_invitations_trip
    ON trip_invitations (trip_id);

BEGIN;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
),
loadtest_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN loadtest_trips trip ON trip.id = tx.trip_id
),
loadtest_posts AS (
    SELECT post.id
    FROM posts post
    JOIN loadtest_trips trip ON trip.id = post.trip_id
)
DELETE FROM post_attachments attachment
USING loadtest_posts post
WHERE attachment.post_id = post.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
),
loadtest_posts AS (
    SELECT post.id
    FROM posts post
    JOIN loadtest_trips trip ON trip.id = post.trip_id
)
DELETE FROM post_comments comment
USING loadtest_posts post
WHERE comment.post_id = post.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM settlement_transfers transfer
USING settlements settlement, loadtest_trips trip
WHERE transfer.settlement_id = settlement.id
  AND settlement.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM settlements settlement
USING loadtest_trips trip
WHERE settlement.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM trip_participant_balance_summaries summary
USING loadtest_trips trip
WHERE summary.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
),
loadtest_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN loadtest_trips trip ON trip.id = tx.trip_id
)
DELETE FROM transaction_events event
USING loadtest_transactions tx
WHERE event.transaction_id = tx.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
),
loadtest_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN loadtest_trips trip ON trip.id = tx.trip_id
)
DELETE FROM transaction_shares share
USING loadtest_transactions tx
WHERE share.transaction_id = tx.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
),
loadtest_transactions AS (
    SELECT tx.id
    FROM transactions tx
    JOIN loadtest_trips trip ON trip.id = tx.trip_id
)
DELETE FROM transaction_payments payment
USING loadtest_transactions tx
WHERE payment.transaction_id = tx.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM posts post
USING loadtest_trips trip
WHERE post.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM transactions tx
USING loadtest_trips trip
WHERE tx.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM trip_exchange_rates rate
USING loadtest_trips trip
WHERE rate.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM trip_countries country
USING loadtest_trips trip
WHERE country.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM trip_invitations invitation
USING loadtest_trips trip
WHERE invitation.trip_id = trip.id;

WITH loadtest_trips AS (
    SELECT id
    FROM trips
    WHERE title = 'LOADTEST_정산_대량_여행'
)
DELETE FROM trip_participants participant
USING loadtest_trips trip
WHERE participant.trip_id = trip.id;

DELETE FROM trips
WHERE title = 'LOADTEST_정산_대량_여행';

DELETE FROM user_agreements agreement
USING users user_row
WHERE agreement.user_id = user_row.id
  AND user_row.nickname LIKE 'LOADTEST_USER_%';

DELETE FROM oauth_accounts account
USING users user_row
WHERE account.user_id = user_row.id
  AND user_row.nickname LIKE 'LOADTEST_USER_%';

DELETE FROM users
WHERE nickname LIKE 'LOADTEST_USER_%';

COMMIT;

ANALYZE users;
ANALYZE trips;
ANALYZE trip_participants;
ANALYZE transactions;
ANALYZE transaction_payments;
ANALYZE transaction_shares;
ANALYZE trip_participant_balance_summaries;
