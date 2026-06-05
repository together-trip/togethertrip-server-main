-- Align persisted schema with the current JPA entities.
-- This migration also normalizes legacy enum values before tightening checks.

UPDATE users
SET status = 'SUSPENDED'
WHERE status = 'INACTIVE';

UPDATE trips
SET settlement_status = 'NOT_STARTED'
WHERE settlement_status = 'OPEN';

UPDATE trip_participants
SET participant_status = 'ACTIVE'
WHERE participant_status = 'PENDING';

UPDATE trip_participants
SET participant_status = 'LEFT'
WHERE participant_status = 'INACTIVE';

UPDATE transactions
SET transaction_type = 'EXPENSE'
WHERE transaction_type IN ('COMMON_FUND_DEPOSIT', 'COMMON_FUND_WITHDRAWAL');

UPDATE posts
SET post_type = CASE post_type
    WHEN 'NORMAL' THEN 'RECORD'
    WHEN 'MEMORY' THEN 'RECORD'
    WHEN 'TRANSACTION_LOG' THEN 'EXPENSE'
    ELSE post_type
END
WHERE post_type IN ('NORMAL', 'MEMORY', 'TRANSACTION_LOG');

UPDATE transaction_events
SET event_type = 'UPDATED'
WHERE event_type = 'ADJUSTED';

UPDATE settlements
SET status = CASE status
    WHEN 'IN_PROGRESS' THEN 'DRAFT'
    WHEN 'COMPLETED' THEN 'CONFIRMED'
    ELSE status
END
WHERE status IN ('IN_PROGRESS', 'COMPLETED');

ALTER TABLE trips
    DROP COLUMN IF EXISTS created_by_user_id,
    DROP COLUMN IF EXISTS name;

ALTER TABLE trip_participants
    DROP COLUMN IF EXISTS participant_type;

ALTER TABLE transactions
    DROP COLUMN IF EXISTS category,
    DROP COLUMN IF EXISTS description,
    DROP COLUMN IF EXISTS location,
    DROP COLUMN IF EXISTS occured_at,
    DROP COLUMN IF EXISTS occurred_at,
    DROP COLUMN IF EXISTS place_name;

ALTER TABLE posts
    DROP COLUMN IF EXISTS visibility;

ALTER TABLE settlements
    DROP COLUMN IF EXISTS total_common_fund_amount;

ALTER TABLE settlement_transfers
    DROP COLUMN IF EXISTS sender_id,
    DROP COLUMN IF EXISTS receiver_id;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_status_check,
    ADD CONSTRAINT users_status_check
        CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'SUSPENDED'));

ALTER TABLE trips
    DROP CONSTRAINT IF EXISTS trips_settlement_status_check,
    ADD CONSTRAINT trips_settlement_status_check
        CHECK (settlement_status IN ('NOT_STARTED', 'IN_PROGRESS', 'SETTLED'));

ALTER TABLE trip_participants
    DROP CONSTRAINT IF EXISTS trip_participants_participant_status_check,
    ADD CONSTRAINT trip_participants_participant_status_check
        CHECK (participant_status IN ('ACTIVE', 'LEFT', 'REMOVED'));

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_transaction_type_check,
    ADD CONSTRAINT transactions_transaction_type_check
        CHECK (transaction_type IN ('EXPENSE'));

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_status_check,
    ADD CONSTRAINT transactions_status_check
        CHECK (status IN ('ACTIVE', 'VOIDED'));

ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS posts_post_type_check,
    ADD CONSTRAINT posts_post_type_check
        CHECK (post_type IN ('RECORD', 'EXPENSE'));

ALTER TABLE transaction_events
    DROP CONSTRAINT IF EXISTS transaction_events_event_type_check,
    ADD CONSTRAINT transaction_events_event_type_check
        CHECK (event_type IN ('CREATED', 'UPDATED', 'VOIDED'));

ALTER TABLE settlements
    DROP CONSTRAINT IF EXISTS settlements_status_check,
    ADD CONSTRAINT settlements_status_check
        CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED'));

ALTER TABLE settlement_transfers
    DROP CONSTRAINT IF EXISTS settlement_transfers_status_check,
    ADD CONSTRAINT settlement_transfers_status_check
        CHECK (status IN ('PENDING', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED', 'COMPLETED', 'CANCELLED'));
