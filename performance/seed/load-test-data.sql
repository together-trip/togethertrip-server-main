-- TogetherTrip main load-test seed data.
--
-- Purpose:
--   Create a large ONGOING trip for k6 read/settlement-preview load testing.
--
-- Generated active rows:
--   new users:                      9
--   trips:                          1
--   trip_participants:             10
--   trip_countries:                 1
--   trip_exchange_rates:            1
--   transactions:             100,000
--   transaction_payments:      100,000
--   transaction_shares:       500,000
--   balance summaries:             10
--
-- This script first hard-deletes previous load-test seed data by running:
--   cleanup-load-test-data.sql
--
-- Run from togethertrip-server-main while gateway docker compose database is up:
--   psql "postgresql://together_trip:together_trip@localhost:5432/together_trip" \
--     -f performance/seed/load-test-data.sql

\ir cleanup-load-test-data.sql

BEGIN;

DO $$
DECLARE
    transaction_count integer := 100000;
    generated_user_count integer := 9;
    participant_count integer := 10;
    base_time timestamptz := TIMESTAMPTZ '2026-06-01 00:00:00+09';
    load_trip_id bigint;
    inserted_transaction_count bigint;
    inserted_payment_count bigint;
    inserted_share_count bigint;
BEGIN
    INSERT INTO users (
        nickname,
        gender,
        birth_date,
        profile_image_url,
        phone_number,
        phone_verified_at,
        role,
        status,
        created_at,
        updated_at
    )
    SELECT
        'LOADTEST_USER_' || lpad(user_no::text, 2, '0'),
        CASE WHEN user_no % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END,
        DATE '1990-01-01' + (user_no % 365),
        NULL,
        NULL,
        TIMESTAMPTZ '2026-06-01 00:00:00+09',
        'USER',
        'ACTIVE',
        base_time,
        base_time
    FROM generate_series(1, generated_user_count) AS user_no;

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
        'local-test-verified:hana',
        '로컬 테스트 verified:hana',
        user_row.profile_image_url,
        base_time,
        base_time,
        NULL
    FROM users user_row
    WHERE user_row.nickname = '로컬 verified hana'
      AND user_row.deleted_at IS NULL
    ON CONFLICT (provider, provider_user_id) DO UPDATE SET
        user_id = EXCLUDED.user_id,
        nickname = EXCLUDED.nickname,
        profile_image_url = EXCLUDED.profile_image_url,
        updated_at = EXCLUDED.updated_at,
        deleted_at = NULL;

    INSERT INTO trips (
        owner_user_id,
        title,
        default_currency,
        exchange_rate_base_date,
        start_date,
        end_date,
        trip_status,
        settlement_status,
        expense_version,
        settled_at,
        created_at,
        updated_at
    )
    SELECT
        user_row.id,
        'LOADTEST_정산_대량_여행',
        'KRW',
        DATE '2026-06-01',
        DATE '2026-06-01',
        DATE '2026-06-10',
        'ONGOING',
        'NOT_STARTED',
        transaction_count,
        NULL,
        base_time,
        base_time
    FROM users user_row
    WHERE user_row.nickname = '로컬 verified hana'
      AND user_row.deleted_at IS NULL
    RETURNING id INTO load_trip_id;

    IF load_trip_id IS NULL THEN
        RAISE EXCEPTION 'Cannot create load-test trip: local sample user "로컬 verified hana" was not found.';
    END IF;

    INSERT INTO trip_countries (
        trip_id,
        country_code,
        country_name,
        sort_order,
        created_at,
        updated_at
    )
    VALUES (
        load_trip_id,
        'KR',
        '대한민국',
        1,
        base_time,
        base_time
    );

    INSERT INTO trip_exchange_rates (
        trip_id,
        base_currency,
        target_currency,
        rate,
        rate_date,
        source,
        created_at,
        updated_at
    )
    VALUES (
        load_trip_id,
        'KRW',
        'KRW',
        1.000000,
        DATE '2026-06-01',
        'LOAD_TEST_SEED',
        base_time,
        base_time
    );

    WITH participant_sources AS (
        SELECT
            user_row.id AS user_id,
            '지민' AS display_name,
            'LEADER' AS participant_role,
            0 AS sort_order
        FROM users user_row
        WHERE user_row.nickname = '로컬 verified hana'
          AND user_row.deleted_at IS NULL

        UNION ALL

        SELECT
            loadtest_user.id AS user_id,
            '부하' || lpad(loadtest_user.row_no::text, 2, '0') AS display_name,
            'MEMBER' AS participant_role,
            loadtest_user.row_no AS sort_order
        FROM (
            SELECT
                user_row.id,
                row_number() OVER (ORDER BY user_row.nickname) AS row_no
            FROM users user_row
            WHERE user_row.nickname LIKE 'LOADTEST_USER_%'
              AND user_row.deleted_at IS NULL
        ) loadtest_user
    )
    INSERT INTO trip_participants (
        trip_id,
        user_id,
        display_name,
        profile_image_url,
        participant_role,
        participant_status,
        joined_at,
        left_at,
        created_at,
        updated_at
    )
    SELECT
        load_trip_id,
        participant_sources.user_id,
        participant_sources.display_name,
        NULL,
        participant_sources.participant_role,
        'ACTIVE',
        base_time,
        NULL,
        base_time,
        base_time
    FROM participant_sources
    ORDER BY participant_sources.sort_order;

    WITH generated_transactions AS (
        SELECT
            series_no,
            load_trip_id AS trip_id,
            owner.id AS created_by_user_id,
            (1000 + (series_no % 90000))::numeric(19, 2) AS amount,
            base_time + (series_no * interval '1 second') AS event_time
        FROM generate_series(1, transaction_count) AS series_no
        CROSS JOIN (
            SELECT user_row.id
            FROM users user_row
            WHERE user_row.nickname = '로컬 verified hana'
              AND user_row.deleted_at IS NULL
        ) owner
    ),
    inserted_transactions AS (
        INSERT INTO transactions (
            trip_id,
            created_by_user_id,
            transaction_type,
            amount,
            currency,
            exchange_rate,
            base_currency,
            base_amount,
            version,
            status,
            created_at,
            updated_at
        )
        SELECT
            trip_id,
            created_by_user_id,
            'EXPENSE',
            amount,
            'KRW',
            1.000000,
            'KRW',
            amount,
            1,
            'ACTIVE',
            event_time,
            event_time
        FROM generated_transactions
        ORDER BY series_no
        RETURNING id, trip_id, amount, created_at
    ),
    numbered_transactions AS (
        SELECT
            tx.id,
            tx.trip_id,
            tx.amount,
            tx.created_at,
            row_number() OVER (ORDER BY tx.id) AS rn
        FROM inserted_transactions tx
    ),
    participant_rows AS (
        SELECT
            participant.id,
            row_number() OVER (ORDER BY participant.id) AS participant_no
        FROM trip_participants participant
        WHERE participant.trip_id = load_trip_id
          AND participant.deleted_at IS NULL
    ),
    inserted_payments AS (
        INSERT INTO transaction_payments (
            transaction_id,
            trip_participant_id,
            amount,
            currency,
            exchange_rate,
            base_currency,
            base_amount,
            created_at,
            updated_at
        )
        SELECT
            tx.id,
            payer.id,
            tx.amount,
            'KRW',
            1.000000,
            'KRW',
            tx.amount,
            tx.created_at,
            tx.created_at
        FROM numbered_transactions tx
        JOIN participant_rows payer
          ON payer.participant_no = ((tx.rn - 1) % participant_count) + 1
        RETURNING transaction_id
    ),
    inserted_shares AS (
        INSERT INTO transaction_shares (
            transaction_id,
            trip_participant_id,
            share_amount,
            currency,
            exchange_rate,
            base_currency,
            base_share_amount,
            share_ratio,
            created_at,
            updated_at
        )
        SELECT
            tx.id,
            participant.id,
            round(tx.amount / 5.0, 2),
            'KRW',
            1.000000,
            'KRW',
            round(tx.amount / 5.0, 2),
            0.2000,
            tx.created_at,
            tx.created_at
        FROM numbered_transactions tx
        JOIN generate_series(0, 4) AS offset_no ON true
        JOIN participant_rows participant
          ON participant.participant_no = ((tx.rn + offset_no - 1) % participant_count) + 1
        RETURNING transaction_id
    )
    SELECT
        (SELECT count(*) FROM numbered_transactions),
        (SELECT count(*) FROM inserted_payments),
        (SELECT count(*) FROM inserted_shares)
    INTO
        inserted_transaction_count,
        inserted_payment_count,
        inserted_share_count;

    INSERT INTO trip_participant_balance_summaries (
        trip_id,
        trip_participant_id,
        paid_base_amount,
        share_base_amount,
        net_base_amount,
        projection_version,
        created_at,
        updated_at
    )
    SELECT
        load_trip_id,
        participant.id,
        coalesce(payment_totals.paid_base_amount, 0),
        coalesce(share_totals.share_base_amount, 0),
        coalesce(payment_totals.paid_base_amount, 0) - coalesce(share_totals.share_base_amount, 0),
        transaction_count,
        now(),
        now()
    FROM trip_participants participant
    LEFT JOIN (
        SELECT payment.trip_participant_id, sum(payment.base_amount) AS paid_base_amount
        FROM transaction_payments payment
        JOIN transactions tx ON tx.id = payment.transaction_id
        WHERE tx.trip_id = load_trip_id
          AND tx.deleted_at IS NULL
          AND payment.deleted_at IS NULL
        GROUP BY payment.trip_participant_id
    ) payment_totals ON payment_totals.trip_participant_id = participant.id
    LEFT JOIN (
        SELECT share.trip_participant_id, sum(share.base_share_amount) AS share_base_amount
        FROM transaction_shares share
        JOIN transactions tx ON tx.id = share.transaction_id
        WHERE tx.trip_id = load_trip_id
          AND tx.deleted_at IS NULL
          AND share.deleted_at IS NULL
        GROUP BY share.trip_participant_id
    ) share_totals ON share_totals.trip_participant_id = participant.id
    WHERE participant.trip_id = load_trip_id
      AND participant.deleted_at IS NULL;

    RAISE NOTICE 'Created load-test trip id %, transactions %, payments %, shares %',
        load_trip_id,
        inserted_transaction_count,
        inserted_payment_count,
        inserted_share_count;
END $$;

COMMIT;

ANALYZE users;
ANALYZE trips;
ANALYZE trip_participants;
ANALYZE transactions;
ANALYZE transaction_payments;
ANALYZE transaction_shares;
ANALYZE trip_participant_balance_summaries;
