\pset pager off

WITH params AS (
    SELECT :'run_id' AS run_id
),
counts AS (
    SELECT
        'same_session_oauth_accounts' AS item,
        1 AS expected,
        COUNT(*) AS actual
    FROM oauth_accounts oa, params p
    WHERE oa.provider = 'KAKAO'
      AND oa.provider_user_id = CONCAT('local-test-issue45-', p.run_id, '-same-session')

    UNION ALL

    SELECT
        'same_session_users' AS item,
        1 AS expected,
        COUNT(DISTINCT u.id) AS actual
    FROM users u
    JOIN oauth_accounts oa ON oa.user_id = u.id
    JOIN params p ON TRUE
    WHERE oa.provider = 'KAKAO'
      AND oa.provider_user_id = CONCAT('local-test-issue45-', p.run_id, '-same-session')
      AND u.deleted_at IS NULL

    UNION ALL

    SELECT
        'same_phone_oauth_accounts' AS item,
        1 AS expected,
        COUNT(*) AS actual
    FROM oauth_accounts oa, params p
    WHERE oa.provider = 'KAKAO'
      AND oa.provider_user_id LIKE CONCAT('local-test-issue45-', p.run_id, '-same-phone-%')

    UNION ALL

    SELECT
        'same_phone_users' AS item,
        1 AS expected,
        COUNT(DISTINCT u.id) AS actual
    FROM users u
    JOIN oauth_accounts oa ON oa.user_id = u.id
    JOIN params p ON TRUE
    WHERE oa.provider = 'KAKAO'
      AND oa.provider_user_id LIKE CONCAT('local-test-issue45-', p.run_id, '-same-phone-%')
      AND u.deleted_at IS NULL

    UNION ALL

    SELECT
        'duplicate_phone_hash_groups' AS item,
        0 AS expected,
        COUNT(*) AS actual
    FROM (
        SELECT u.phone_number_hash
        FROM users u
        JOIN oauth_accounts oa ON oa.user_id = u.id
        JOIN params p ON TRUE
        WHERE oa.provider = 'KAKAO'
          AND oa.provider_user_id LIKE CONCAT('local-test-issue45-', p.run_id, '-%')
          AND u.deleted_at IS NULL
          AND u.phone_number_hash IS NOT NULL
        GROUP BY u.phone_number_hash
        HAVING COUNT(*) > 1
    ) duplicate_groups
)
SELECT
    item,
    expected,
    actual,
    CASE WHEN expected = actual THEN 'OK' ELSE 'MISMATCH' END AS result
FROM counts
ORDER BY item;
