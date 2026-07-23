-- Local Swagger sample data.
-- Suggested tokens:
--   local-test:verified:hana
--   local-test:verified:minseo
--   local-test:verified:joon
--   local-test:unverified:yuri
--   local-test:admin

MERGE INTO users AS u
USING (
    VALUES
        ('로컬 admin', NULL, NULL, NULL, 'v1:AAAAAAAAAAAAAAAB:cPIdEt7BDh8WjIhKty5ZBY6IJ+JFLgngB//u2Y8=', 'v1', '010-****-4463', '3e2edec33184e8f112051ac1eb0876c0c65676a1ef96ca0d4f7e098a6818dfff', 'v1', TIMESTAMPTZ '2026-05-01T08:55:00+09:00', 'ADMIN', 'ACTIVE', TIMESTAMPTZ '2026-05-01T08:55:00+09:00', TIMESTAMPTZ '2026-05-01T08:55:00+09:00', NULL::timestamptz),
        ('로컬 verified hana', 'FEMALE', DATE '1993-04-12', '/uploads/user-profile-images/local-hana.jpg', 'v1:AAAAAAAAAAAAAAAC:rTrCzBM/YaOylQHtHw+eT6EVzufrE9HfbmhhGNQ=', 'v1', '010-****-1860', 'd09113b049cb3640e4d64db43331d68f325b18f0b3be9dfdd1e6d503c8b3bc58', 'v1', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', 'USER', 'ACTIVE', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', TIMESTAMPTZ '2026-05-20T13:10:00+09:00', NULL::timestamptz),
        ('로컬 verified minseo', 'FEMALE', DATE '1994-11-03', '/uploads/user-profile-images/local-minseo.jpg', 'v1:AAAAAAAAAAAAAAAD:HdR0nEl9e0E6vr9r0AxF+W0y1Ar7i8StEDotK78=', 'v1', '010-****-8861', '4a14f641c38bb900c4e582fea7f760b5485d1e5abb74216fc72cef86046b4382', 'v1', TIMESTAMPTZ '2026-05-01T09:05:00+09:00', 'USER', 'ACTIVE', TIMESTAMPTZ '2026-05-01T09:05:00+09:00', TIMESTAMPTZ '2026-05-20T13:12:00+09:00', NULL::timestamptz),
        ('로컬 verified joon', 'MALE', DATE '1991-08-27', '/uploads/user-profile-images/local-joon.jpg', 'v1:AAAAAAAAAAAAAAAE:Ljx4x3KyJDhuaFkoxAdJx+2yWInvo5arv012mLI=', 'v1', '010-****-4940', '613c01f3d2e2305b882ecdfcc6a9bfe517f7f19d7096354fa3f49dac585dc52c', 'v1', TIMESTAMPTZ '2026-05-01T09:10:00+09:00', 'USER', 'ACTIVE', TIMESTAMPTZ '2026-05-01T09:10:00+09:00', TIMESTAMPTZ '2026-05-20T13:15:00+09:00', NULL::timestamptz),
        ('로컬 unverified yuri', 'FEMALE', DATE '1996-02-18', '/uploads/user-profile-images/local-yuri.jpg', NULL, NULL, NULL, NULL, NULL, NULL::timestamptz, 'USER', 'ACTIVE', TIMESTAMPTZ '2026-05-01T09:15:00+09:00', TIMESTAMPTZ '2026-05-20T13:17:00+09:00', NULL::timestamptz),
        ('정우 휴면계정', 'MALE', DATE '1989-12-09', NULL, 'v1:AAAAAAAAAAAAAAAF:HIgTOEbhocXLirDc48frlkNlwDE+v/ulw8QnAqc=', 'v1', '010-****-9354', '737fc8c881aaefda9ba78d5587ad0fade3003336620fc49b06b08e0a4ef955e9', 'v1', TIMESTAMPTZ '2026-04-01T10:00:00+09:00', 'USER', 'SUSPENDED', TIMESTAMPTZ '2026-04-01T10:00:00+09:00', TIMESTAMPTZ '2026-05-10T08:00:00+09:00', NULL::timestamptz),
        ('탈퇴한 수아', 'FEMALE', DATE '1995-07-21', NULL, 'v1:AAAAAAAAAAAAAAAG:VohT09sh7p3FFRM5QZBl4rp3f5Pj3sOVHlxqKhw=', 'v1', '010-****-0428', '2b79da0b34f7e4b6bd98093f57d11ffb00615f825961654d4d28066d3cfcc502', 'v1', TIMESTAMPTZ '2026-03-19T11:00:00+09:00', 'USER', 'WITHDRAWN', TIMESTAMPTZ '2026-03-19T11:00:00+09:00', TIMESTAMPTZ '2026-05-18T20:00:00+09:00', TIMESTAMPTZ '2026-05-18T20:00:00+09:00')
) AS sample(nickname, gender, birth_date, profile_image_url, phone_number_encrypted, phone_number_encryption_version, phone_number_masked, phone_number_hash, phone_number_hash_version, phone_verified_at, role, status, created_at, updated_at, deleted_at)
ON u.nickname = sample.nickname
WHEN MATCHED THEN
    UPDATE SET
        gender = sample.gender,
        birth_date = sample.birth_date,
        profile_image_url = sample.profile_image_url,
        phone_number = NULL,
        phone_number_encrypted = sample.phone_number_encrypted,
        phone_number_encryption_version = sample.phone_number_encryption_version,
        phone_number_masked = sample.phone_number_masked,
        phone_number_hash = sample.phone_number_hash,
        phone_number_hash_version = sample.phone_number_hash_version,
        phone_verified_at = sample.phone_verified_at,
        role = sample.role,
        status = sample.status,
        updated_at = sample.updated_at,
        deleted_at = sample.deleted_at
WHEN NOT MATCHED THEN
    INSERT (
        nickname, gender, birth_date, profile_image_url,
        phone_number_encrypted, phone_number_encryption_version, phone_number_masked,
        phone_number_hash, phone_number_hash_version, phone_verified_at,
        role, status, created_at, updated_at, deleted_at
    )
    VALUES (
        sample.nickname, sample.gender, sample.birth_date, sample.profile_image_url,
        sample.phone_number_encrypted, sample.phone_number_encryption_version, sample.phone_number_masked,
        sample.phone_number_hash, sample.phone_number_hash_version, sample.phone_verified_at,
        sample.role, sample.status, sample.created_at, sample.updated_at, sample.deleted_at
    );

INSERT INTO oauth_accounts (
    user_id, provider, provider_user_id, nickname, profile_image_url, created_at, updated_at, deleted_at
)
SELECT u.id, 'KAKAO', sample.provider_user_id, sample.nickname, sample.profile_image_url, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('로컬 verified hana', 'local-kakao-hana', '한지민', '/uploads/user-profile-images/local-hana.jpg', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', TIMESTAMPTZ '2026-05-01T09:00:00+09:00'),
        ('로컬 verified minseo', 'local-kakao-minseo', '김민서', '/uploads/user-profile-images/local-minseo.jpg', TIMESTAMPTZ '2026-05-01T09:05:00+09:00', TIMESTAMPTZ '2026-05-01T09:05:00+09:00'),
        ('로컬 verified joon', 'local-kakao-joon', '박서준', '/uploads/user-profile-images/local-joon.jpg', TIMESTAMPTZ '2026-05-01T09:10:00+09:00', TIMESTAMPTZ '2026-05-01T09:10:00+09:00'),
        ('로컬 unverified yuri', 'local-kakao-yuri', '이유리', '/uploads/user-profile-images/local-yuri.jpg', TIMESTAMPTZ '2026-05-01T09:15:00+09:00', TIMESTAMPTZ '2026-05-01T09:15:00+09:00')
) AS sample(user_nickname, provider_user_id, nickname, profile_image_url, created_at, updated_at)
JOIN users u ON u.nickname = sample.user_nickname
ON CONFLICT (provider, provider_user_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    nickname = EXCLUDED.nickname,
    profile_image_url = EXCLUDED.profile_image_url,
    updated_at = EXCLUDED.updated_at,
    deleted_at = EXCLUDED.deleted_at;

INSERT INTO user_agreements (
    user_id, agreement_type, agreed, term_version, agreed_at, revoked_at, created_at, updated_at, deleted_at
)
SELECT u.id, sample.agreement_type, sample.agreed, sample.term_version, sample.agreed_at, sample.revoked_at, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('로컬 verified hana', 'SERVICE_TERMS', true, '2026-06-18', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-01T09:00:00+09:00', TIMESTAMPTZ '2026-05-01T09:00:00+09:00'),
        ('로컬 verified hana', 'PRIVACY_POLICY', true, '2026-06-18', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-01T09:00:00+09:00', TIMESTAMPTZ '2026-05-01T09:00:00+09:00'),
        ('로컬 verified hana', 'LOCATION_INFO_TERMS', true, '2026-06-18', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-01T09:00:00+09:00', TIMESTAMPTZ '2026-05-01T09:00:00+09:00'),
        ('로컬 verified hana', 'MARKETING_CONSENT', false, '2026-06-18', NULL::timestamptz, TIMESTAMPTZ '2026-05-15T12:00:00+09:00', TIMESTAMPTZ '2026-05-01T09:00:00+09:00', TIMESTAMPTZ '2026-05-15T12:00:00+09:00'),
        ('로컬 verified minseo', 'SERVICE_TERMS', true, '2026-06-18', TIMESTAMPTZ '2026-05-01T09:05:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-01T09:05:00+09:00', TIMESTAMPTZ '2026-05-01T09:05:00+09:00'),
        ('로컬 verified joon', 'SERVICE_TERMS', true, '2026-06-18', TIMESTAMPTZ '2026-05-01T09:10:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-01T09:10:00+09:00', TIMESTAMPTZ '2026-05-01T09:10:00+09:00')
) AS sample(user_nickname, agreement_type, agreed, term_version, agreed_at, revoked_at, created_at, updated_at)
JOIN users u ON u.nickname = sample.user_nickname
WHERE NOT EXISTS (
    SELECT 1
    FROM user_agreements ua
    WHERE ua.user_id = u.id
      AND ua.agreement_type = sample.agreement_type
);

INSERT INTO trips (
    owner_user_id, title, default_currency, exchange_rate_base_date, start_date, end_date,
    trip_status, settlement_status, expense_version, settled_at, created_at, updated_at, deleted_at
)
SELECT owner.id, sample.title, sample.default_currency, sample.exchange_rate_base_date, sample.start_date, sample.end_date,
       sample.trip_status, sample.settlement_status, sample.expense_version, sample.settled_at,
       sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('로컬 verified hana', '오사카 3박4일 맛집 여행', 'KRW', DATE '2026-05-10', DATE '2026-05-10', DATE '2026-05-13', 'COMPLETED', 'SETTLED', 6::bigint, TIMESTAMPTZ '2026-05-15T21:00:00+09:00', TIMESTAMPTZ '2026-04-20T22:10:00+09:00', TIMESTAMPTZ '2026-05-15T21:00:00+09:00', NULL::timestamptz),
        ('로컬 verified hana', '다낭 여름 휴가', 'KRW', DATE '2026-06-01', DATE '2026-06-02', DATE '2026-06-07', 'ONGOING', 'IN_PROGRESS', 4::bigint, NULL::timestamptz, TIMESTAMPTZ '2026-05-18T20:30:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('로컬 verified hana', '제주 렌터카 여행 준비', 'KRW', NULL::date, DATE '2026-07-12', DATE '2026-07-14', 'PLANNED', 'NOT_STARTED', 0::bigint, NULL::timestamptz, TIMESTAMPTZ '2026-05-25T19:00:00+09:00', TIMESTAMPTZ '2026-05-25T19:00:00+09:00', NULL::timestamptz),
        ('로컬 verified minseo', '방콕 송크란 여행 취소 기록', 'KRW', DATE '2026-04-10', DATE '2026-04-11', DATE '2026-04-15', 'PLANNED', 'NOT_STARTED', 1::bigint, NULL::timestamptz, TIMESTAMPTZ '2026-03-01T12:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(owner_nickname, title, default_currency, exchange_rate_base_date, start_date, end_date, trip_status, settlement_status, expense_version, settled_at, created_at, updated_at, deleted_at)
JOIN users owner ON owner.nickname = sample.owner_nickname
WHERE NOT EXISTS (
    SELECT 1 FROM trips t WHERE t.title = sample.title AND t.owner_user_id = owner.id
);

INSERT INTO trip_countries (
    trip_id, country_code, country_name, sort_order, created_at, updated_at, deleted_at
)
SELECT t.id, sample.country_code, sample.country_name, sample.sort_order, sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', 'JP', '일본', 1, TIMESTAMPTZ '2026-04-20T22:15:00+09:00', TIMESTAMPTZ '2026-04-20T22:15:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', 'VN', '베트남', 1, TIMESTAMPTZ '2026-05-18T20:35:00+09:00', TIMESTAMPTZ '2026-05-18T20:35:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', 'KR', '대한민국', 1, TIMESTAMPTZ '2026-05-25T19:05:00+09:00', TIMESTAMPTZ '2026-05-25T19:05:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', 'TH', '태국', 1, TIMESTAMPTZ '2026-03-01T12:05:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, country_code, country_name, sort_order, created_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
WHERE NOT EXISTS (
    SELECT 1
    FROM trip_countries tc
    WHERE tc.trip_id = t.id
      AND tc.country_code = sample.country_code
);

INSERT INTO trip_exchange_rates (
    trip_id, base_currency, target_currency, rate, rate_date, source, created_at, updated_at, deleted_at
)
SELECT t.id, sample.base_currency, sample.target_currency, sample.rate, sample.rate_date, sample.source,
       sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', 'KRW', 'KRW', 1.000000::numeric, DATE '2026-05-10', 'BASE_CURRENCY', TIMESTAMPTZ '2026-05-10T08:00:00+09:00', TIMESTAMPTZ '2026-05-10T08:00:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', 'KRW', 'JPY', 9.149131::numeric, DATE '2026-05-10', 'KEB_HANA_DAILY', TIMESTAMPTZ '2026-05-10T08:00:00+09:00', TIMESTAMPTZ '2026-05-10T08:00:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', 'KRW', 'KRW', 1.000000::numeric, DATE '2026-06-01', 'BASE_CURRENCY', TIMESTAMPTZ '2026-06-01T08:00:00+09:00', TIMESTAMPTZ '2026-06-01T08:00:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', 'KRW', 'VND', 0.054289::numeric, DATE '2026-06-01', 'KEB_HANA_DAILY', TIMESTAMPTZ '2026-06-01T08:00:00+09:00', TIMESTAMPTZ '2026-06-01T08:00:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', 'KRW', 'KRW', 1.000000::numeric, DATE '2026-07-12', 'BASE_CURRENCY', TIMESTAMPTZ '2026-07-12T08:00:00+09:00', TIMESTAMPTZ '2026-07-12T08:00:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', 'KRW', 'KRW', 1.000000::numeric, DATE '2026-04-10', 'BASE_CURRENCY', TIMESTAMPTZ '2026-04-10T08:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00'),
        ('방콕 송크란 여행 취소 기록', 'KRW', 'THB', 38.167939::numeric, DATE '2026-04-10', 'KEB_HANA_DAILY', TIMESTAMPTZ '2026-04-10T08:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, base_currency, target_currency, rate, rate_date, source, created_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
WHERE NOT EXISTS (
    SELECT 1
    FROM trip_exchange_rates ter
    WHERE ter.trip_id = t.id
      AND ter.base_currency = sample.base_currency
      AND ter.target_currency = sample.target_currency
      AND ter.rate_date = sample.rate_date
);

INSERT INTO exchange_rates (
    base_currency, target_currency, rate, rate_date, source, created_at, updated_at, deleted_at
)
SELECT sample.base_currency, sample.target_currency, sample.rate, sample.rate_date, sample.source,
       sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('KRW', 'JPY', 9.150000::numeric, DATE '2026-06-08', 'LOCAL_BATCH_SAMPLE', TIMESTAMPTZ '2026-06-08T08:00:00+09:00', TIMESTAMPTZ '2026-06-08T08:00:00+09:00'),
        ('KRW', 'VND', 0.054000::numeric, DATE '2026-06-08', 'LOCAL_BATCH_SAMPLE', TIMESTAMPTZ '2026-06-08T08:00:00+09:00', TIMESTAMPTZ '2026-06-08T08:00:00+09:00'),
        ('KRW', 'THB', 38.170000::numeric, DATE '2026-06-08', 'LOCAL_BATCH_SAMPLE', TIMESTAMPTZ '2026-06-08T08:00:00+09:00', TIMESTAMPTZ '2026-06-08T08:00:00+09:00'),
        ('KRW', 'USD', 1350.000000::numeric, DATE '2026-06-08', 'LOCAL_BATCH_SAMPLE', TIMESTAMPTZ '2026-06-08T08:00:00+09:00', TIMESTAMPTZ '2026-06-08T08:00:00+09:00')
) AS sample(base_currency, target_currency, rate, rate_date, source, created_at, updated_at)
ON CONFLICT (base_currency, target_currency, rate_date) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO trip_participants (
    trip_id, user_id, display_name, profile_image_url, participant_role, participant_status,
    joined_at, left_at, created_at, updated_at, deleted_at
)
SELECT t.id, u.id, sample.display_name, sample.profile_image_url, sample.participant_role, sample.participant_status,
       sample.joined_at, sample.left_at, sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', '로컬 verified hana', '지민', '/uploads/user-profile-images/local-hana.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-04-20T22:20:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-04-20T22:20:00+09:00', TIMESTAMPTZ '2026-05-15T21:00:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', '로컬 verified minseo', '민서', '/uploads/user-profile-images/local-minseo.jpg', 'MEMBER', 'ACTIVE', TIMESTAMPTZ '2026-04-21T09:30:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-04-21T09:30:00+09:00', TIMESTAMPTZ '2026-05-15T21:00:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', '로컬 verified joon', '서준', '/uploads/user-profile-images/local-joon.jpg', 'MEMBER', 'LEFT', TIMESTAMPTZ '2026-04-22T10:00:00+09:00', TIMESTAMPTZ '2026-05-13T23:20:00+09:00', TIMESTAMPTZ '2026-04-22T10:00:00+09:00', TIMESTAMPTZ '2026-05-13T23:20:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 verified hana', '지민', '/uploads/user-profile-images/local-hana.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-05-18T20:40:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-18T20:40:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 verified minseo', '민서', '/uploads/user-profile-images/local-minseo.jpg', 'MEMBER', 'ACTIVE', TIMESTAMPTZ '2026-05-18T20:45:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-18T20:45:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 unverified yuri', '유리', '/uploads/user-profile-images/local-yuri.jpg', 'MEMBER', 'ACTIVE', TIMESTAMPTZ '2026-05-19T08:00:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-19T08:00:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', '로컬 verified hana', '지민', '/uploads/user-profile-images/local-hana.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-05-25T19:10:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-25T19:10:00+09:00', TIMESTAMPTZ '2026-05-25T19:10:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', '로컬 verified minseo', '민서', '/uploads/user-profile-images/local-minseo.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-03-01T12:10:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-03-01T12:10:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, user_nickname, display_name, profile_image_url, participant_role, participant_status, joined_at, left_at, created_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
JOIN users u ON u.nickname = sample.user_nickname
WHERE NOT EXISTS (
    SELECT 1
    FROM trip_participants tp
    WHERE tp.trip_id = t.id
      AND tp.user_id = u.id
);

UPDATE trip_participants AS tp
SET
    display_name = sample.display_name,
    profile_image_url = sample.profile_image_url,
    participant_role = sample.participant_role,
    participant_status = sample.participant_status,
    joined_at = sample.joined_at,
    left_at = sample.left_at,
    updated_at = sample.updated_at,
    deleted_at = sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', '로컬 verified hana', '지민', '/uploads/user-profile-images/local-hana.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-04-20T22:20:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-15T21:00:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', '로컬 verified minseo', '민서', '/uploads/user-profile-images/local-minseo.jpg', 'MEMBER', 'ACTIVE', TIMESTAMPTZ '2026-04-21T09:30:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-15T21:00:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', '로컬 verified joon', '서준', '/uploads/user-profile-images/local-joon.jpg', 'MEMBER', 'LEFT', TIMESTAMPTZ '2026-04-22T10:00:00+09:00', TIMESTAMPTZ '2026-05-13T23:20:00+09:00', TIMESTAMPTZ '2026-05-13T23:20:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 verified hana', '지민', '/uploads/user-profile-images/local-hana.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-05-18T20:40:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 verified minseo', '민서', '/uploads/user-profile-images/local-minseo.jpg', 'MEMBER', 'ACTIVE', TIMESTAMPTZ '2026-05-18T20:45:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 unverified yuri', '유리', '/uploads/user-profile-images/local-yuri.jpg', 'MEMBER', 'ACTIVE', TIMESTAMPTZ '2026-05-19T08:00:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-06-04T18:40:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', '로컬 verified hana', '지민', '/uploads/user-profile-images/local-hana.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-05-25T19:10:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-25T19:10:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', '로컬 verified minseo', '민서', '/uploads/user-profile-images/local-minseo.jpg', 'LEADER', 'ACTIVE', TIMESTAMPTZ '2026-03-01T12:10:00+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, user_nickname, display_name, profile_image_url, participant_role, participant_status, joined_at, left_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
JOIN users u ON u.nickname = sample.user_nickname
WHERE tp.trip_id = t.id
  AND tp.user_id = u.id;

INSERT INTO trip_participants (
    trip_id, user_id, display_name, profile_image_url, participant_role, participant_status,
    joined_at, left_at, created_at, updated_at, deleted_at
)
SELECT t.id, NULL, sample.display_name, NULL, sample.participant_role, sample.participant_status,
       sample.joined_at, sample.left_at, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', '현장 합류 태오', 'MEMBER', 'REMOVED', TIMESTAMPTZ '2026-05-10T13:00:00+09:00', TIMESTAMPTZ '2026-05-11T09:00:00+09:00', TIMESTAMPTZ '2026-05-10T13:00:00+09:00', TIMESTAMPTZ '2026-05-11T09:00:00+09:00'),
        ('제주 렌터카 여행 준비', '초대 예정 은호', 'MEMBER', 'ACTIVE', NULL::timestamptz, NULL::timestamptz, TIMESTAMPTZ '2026-05-25T19:15:00+09:00', TIMESTAMPTZ '2026-05-25T19:15:00+09:00')
) AS sample(trip_title, display_name, participant_role, participant_status, joined_at, left_at, created_at, updated_at)
JOIN trips t ON t.title = sample.trip_title
WHERE NOT EXISTS (
    SELECT 1 FROM trip_participants tp
    WHERE tp.trip_id = t.id
      AND tp.display_name = sample.display_name
      AND tp.user_id IS NULL
);

DELETE FROM trip_invitations
WHERE token IN (
    'OSAKA-USED-MINSEO',
    'DANANG-ACTIVE-2026',
    'JEJU-EXPIRED-EUNHO',
    'JEJU-CANCELLED-OLD',
    'BANGKOK-DELETED'
)
  AND invite_url LIKE 'https://togethertrip.local/invites/%';

INSERT INTO trip_invitations (
    trip_id, token, code, invite_url, invitation_type, created_by_user_id, used_by_user_id, invitation_status,
    expires_at, used_at, created_at, updated_at, deleted_at
)
SELECT t.id, sample.token, sample.code, sample.invite_url, sample.invitation_type, creator.id, used_by.id, sample.invitation_status,
       sample.expires_at, sample.used_at, sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', 'OSAKA-USED-MINSEO', NULL, 'https://togethertrip.local/invites?token=OSAKA-USED-MINSEO', 'LINK', '로컬 verified hana', '로컬 verified minseo', 'USED', TIMESTAMPTZ '2026-05-08T23:59:59+09:00', TIMESTAMPTZ '2026-04-21T09:30:00+09:00', TIMESTAMPTZ '2026-04-20T22:25:00+09:00', TIMESTAMPTZ '2026-04-21T09:30:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', 'DANANG-ACTIVE-2026', 'DN2026AB', 'https://togethertrip.local/invites?code=DN2026AB', 'CODE', '로컬 verified hana', NULL, 'ACTIVE', TIMESTAMPTZ '2026-06-10T23:59:59+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-18T20:50:00+09:00', TIMESTAMPTZ '2026-05-18T20:50:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', 'JEJU-EXPIRED-EUNHO', 'JEJU2607', 'https://togethertrip.local/invites?code=JEJU2607', 'CODE', '로컬 verified hana', NULL, 'EXPIRED', TIMESTAMPTZ '2026-05-31T23:59:59+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-25T19:20:00+09:00', TIMESTAMPTZ '2026-06-01T00:10:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', 'JEJU-CANCELLED-OLD', NULL, 'https://togethertrip.local/invites?token=JEJU-CANCELLED-OLD', 'LINK', '로컬 verified hana', NULL, 'CANCELLED', TIMESTAMPTZ '2026-06-20T23:59:59+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-05-26T10:00:00+09:00', TIMESTAMPTZ '2026-05-27T10:00:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', 'BANGKOK-DELETED', NULL, 'https://togethertrip.local/invites?token=BANGKOK-DELETED', 'LINK', '로컬 verified minseo', NULL, 'CANCELLED', TIMESTAMPTZ '2026-04-01T23:59:59+09:00', NULL::timestamptz, TIMESTAMPTZ '2026-03-01T12:20:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, token, code, invite_url, invitation_type, creator_nickname, used_by_nickname, invitation_status, expires_at, used_at, created_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
JOIN users creator ON creator.nickname = sample.creator_nickname
LEFT JOIN users used_by ON used_by.nickname = sample.used_by_nickname
WHERE NOT EXISTS (
    SELECT 1
    FROM trip_invitations ti
    WHERE ti.token = sample.token
);

INSERT INTO transactions (
    trip_id, created_by_user_id, transaction_type, amount, currency, exchange_rate, base_currency,
    base_amount, version, status, created_at, updated_at, deleted_at
)
SELECT t.id, u.id, 'EXPENSE', sample.amount, sample.currency, sample.exchange_rate, sample.base_currency,
       sample.base_amount, sample.version, sample.status, sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', '로컬 verified hana', 18400.00::numeric, 'JPY', 9.149131::numeric, 'KRW', 168344.00::numeric, 0::bigint, 'ACTIVE', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', '로컬 verified minseo', 9200.00::numeric, 'JPY', 9.149131::numeric, 'KRW', 84172.00::numeric, 1::bigint, 'VOIDED', TIMESTAMPTZ '2026-05-11T19:30:00+09:00', TIMESTAMPTZ '2026-05-11T20:00:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', '로컬 verified joon', 126000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 126000.00::numeric, 0::bigint, 'ACTIVE', TIMESTAMPTZ '2026-05-12T21:10:00+09:00', TIMESTAMPTZ '2026-05-12T21:10:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 verified hana', 1280000.00::numeric, 'VND', 0.054289::numeric, 'KRW', 69500.00::numeric, 0::bigint, 'ACTIVE', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', '로컬 verified minseo', 48000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 48000.00::numeric, 0::bigint, 'ACTIVE', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', '로컬 verified minseo', 3900.00::numeric, 'THB', 38.167939::numeric, 'KRW', 148855.00::numeric, 0::bigint, 'ACTIVE', TIMESTAMPTZ '2026-03-05T18:30:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, created_by_nickname, amount, currency, exchange_rate, base_currency, base_amount, version, status, created_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
JOIN users u ON u.nickname = sample.created_by_nickname
WHERE NOT EXISTS (
    SELECT 1
    FROM transactions tx
    WHERE tx.trip_id = t.id
      AND tx.created_by_user_id = u.id
      AND tx.amount = sample.amount
      AND tx.currency = sample.currency
      AND tx.created_at = sample.created_at
);

INSERT INTO transaction_payments (
    transaction_id, trip_participant_id, amount, currency, exchange_rate, base_currency,
    base_amount, created_at, updated_at, deleted_at
)
SELECT tx.id, payer.id, sample.amount, sample.currency, sample.exchange_rate, sample.base_currency,
       sample.base_amount, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', '지민', 18400.00::numeric, 'JPY', 9.149131::numeric, 'KRW', 168344.00::numeric, TIMESTAMPTZ '2026-05-10T12:40:00+09:00', TIMESTAMPTZ '2026-05-10T12:40:00+09:00'),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-12T21:10:00+09:00', '서준', 126000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 126000.00::numeric, TIMESTAMPTZ '2026-05-12T21:10:00+09:00', TIMESTAMPTZ '2026-05-12T21:10:00+09:00'),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', '지민', 1280000.00::numeric, 'VND', 0.054289::numeric, 'KRW', 69500.00::numeric, TIMESTAMPTZ '2026-06-02T13:20:00+09:00', TIMESTAMPTZ '2026-06-02T13:20:00+09:00'),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', '민서', 48000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 48000.00::numeric, TIMESTAMPTZ '2026-06-03T22:05:00+09:00', TIMESTAMPTZ '2026-06-03T22:05:00+09:00')
) AS sample(trip_title, transaction_created_at, payer_display_name, amount, currency, exchange_rate, base_currency, base_amount, created_at, updated_at)
JOIN trips t ON t.title = sample.trip_title
JOIN transactions tx ON tx.trip_id = t.id AND tx.created_at = sample.transaction_created_at
JOIN trip_participants payer ON payer.trip_id = t.id AND payer.display_name = sample.payer_display_name
WHERE NOT EXISTS (
    SELECT 1 FROM transaction_payments tp WHERE tp.transaction_id = tx.id AND tp.trip_participant_id = payer.id
);

INSERT INTO transaction_shares (
    transaction_id, trip_participant_id, share_amount, currency, exchange_rate, base_currency,
    base_share_amount, share_ratio, created_at, updated_at, deleted_at
)
SELECT tx.id, participant.id, sample.share_amount, sample.currency, sample.exchange_rate, sample.base_currency,
       sample.base_share_amount, sample.share_ratio, sample.transaction_created_at, sample.transaction_created_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', '지민', 6133.33::numeric, 'JPY', 9.149131::numeric, 'KRW', 56114.67::numeric, 0.3333::numeric),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', '민서', 6133.33::numeric, 'JPY', 9.149131::numeric, 'KRW', 56114.67::numeric, 0.3333::numeric),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', '서준', 6133.34::numeric, 'JPY', 9.149131::numeric, 'KRW', 56114.66::numeric, 0.3334::numeric),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-12T21:10:00+09:00', '지민', 42000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 42000.00::numeric, 0.3333::numeric),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-12T21:10:00+09:00', '민서', 42000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 42000.00::numeric, 0.3333::numeric),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-12T21:10:00+09:00', '서준', 42000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 42000.00::numeric, 0.3334::numeric),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', '지민', 426666.67::numeric, 'VND', 0.054289::numeric, 'KRW', 23166.67::numeric, 0.3333::numeric),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', '민서', 426666.67::numeric, 'VND', 0.054289::numeric, 'KRW', 23166.67::numeric, 0.3333::numeric),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', '유리', 426666.66::numeric, 'VND', 0.054289::numeric, 'KRW', 23166.66::numeric, 0.3334::numeric),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', '지민', 16000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 16000.00::numeric, 0.3333::numeric),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', '민서', 16000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 16000.00::numeric, 0.3333::numeric),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', '유리', 16000.00::numeric, 'KRW', 1.000000::numeric, 'KRW', 16000.00::numeric, 0.3334::numeric)
) AS sample(trip_title, transaction_created_at, participant_display_name, share_amount, currency, exchange_rate, base_currency, base_share_amount, share_ratio)
JOIN trips t ON t.title = sample.trip_title
JOIN transactions tx ON tx.trip_id = t.id AND tx.created_at = sample.transaction_created_at
JOIN trip_participants participant ON participant.trip_id = t.id AND participant.display_name = sample.participant_display_name
WHERE NOT EXISTS (
    SELECT 1 FROM transaction_shares ts WHERE ts.transaction_id = tx.id AND ts.trip_participant_id = participant.id
);

INSERT INTO posts (
    trip_id, transaction_id, author_participant_id, post_type, title, category, content,
    occurred_at, place_name, latitude, longitude, comment_count, created_at, updated_at, deleted_at
)
SELECT t.id, tx.id, author.id, sample.post_type, sample.title, sample.category, sample.content,
       sample.occurred_at, sample.place_name, sample.latitude, sample.longitude, sample.comment_count,
       sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', NULL::timestamptz, '지민', 'RECORD', '도톤보리 첫날 동선 정리', 'SCHEDULE', '난바역에서 숙소 체크인 후 이치란, 도톤보리 산책, 편의점 장보기까지 마무리.', TIMESTAMPTZ '2026-05-10T22:30:00+09:00', '도톤보리', 34.6687000::numeric, 135.5010000::numeric, 2, TIMESTAMPTZ '2026-05-10T22:45:00+09:00', TIMESTAMPTZ '2026-05-10T22:45:00+09:00', NULL::timestamptz),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', '지민', 'EXPENSE', '쿠로몬시장 점심 결제', 'FOOD', '참치덮밥, 타코야키, 생맥주를 한 번에 결제.', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', '쿠로몬시장', 34.6653000::numeric, 135.5066000::numeric, 1, TIMESTAMPTZ '2026-05-10T12:45:00+09:00', TIMESTAMPTZ '2026-05-10T12:45:00+09:00', NULL::timestamptz),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', '지민', 'EXPENSE', '미케비치 근처 반쎄오', 'FOOD', '현금 결제해서 VND로 기록. 셋이 나눠 먹기 충분했음.', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', 'Banh Xeo Ba Duong', 16.0544000::numeric, 108.2022000::numeric, 1, TIMESTAMPTZ '2026-06-02T13:30:00+09:00', TIMESTAMPTZ '2026-06-02T13:30:00+09:00', NULL::timestamptz),
        ('제주 렌터카 여행 준비', NULL::timestamptz, '지민', 'RECORD', '렌터카 후보', 'TODO', '공항 인수 기준으로 완전자차 포함 견적 비교 필요.', NULL::timestamptz, '제주국제공항', 33.5113000::numeric, 126.4930000::numeric, 0, TIMESTAMPTZ '2026-05-25T19:25:00+09:00', TIMESTAMPTZ '2026-05-25T19:25:00+09:00', NULL::timestamptz),
        ('방콕 송크란 여행 취소 기록', TIMESTAMPTZ '2026-03-05T18:30:00+09:00', '민서', 'EXPENSE', '취소된 방콕 숙소 예약금', 'LODGING', '여행 취소로 삭제된 결제 기록.', TIMESTAMPTZ '2026-03-05T18:30:00+09:00', 'Siam', 13.7466000::numeric, 100.5347000::numeric, 0, TIMESTAMPTZ '2026-03-05T18:35:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(trip_title, transaction_created_at, author_display_name, post_type, title, category, content, occurred_at, place_name, latitude, longitude, comment_count, created_at, updated_at, deleted_at)
JOIN trips t ON t.title = sample.trip_title
JOIN trip_participants author ON author.trip_id = t.id AND author.display_name = sample.author_display_name
LEFT JOIN transactions tx ON tx.trip_id = t.id AND tx.created_at = sample.transaction_created_at
WHERE NOT EXISTS (
    SELECT 1 FROM posts p WHERE p.trip_id = t.id AND p.title = sample.title
);

INSERT INTO post_comments (
    post_id, parent_comment_id, author_participant_id, content, comment_depth,
    created_at, updated_at, deleted_at
)
SELECT p.id, parent.id, author.id, sample.content, sample.comment_depth,
       sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('도톤보리 첫날 동선 정리', NULL, '오사카 3박4일 맛집 여행', '민서', '둘째 날 우메다 일정은 오전에 넣는 게 덜 붐빌 듯.', 0, TIMESTAMPTZ '2026-05-10T23:00:00+09:00', TIMESTAMPTZ '2026-05-10T23:00:00+09:00', NULL::timestamptz),
        ('도톤보리 첫날 동선 정리', '둘째 날 우메다 일정은 오전에 넣는 게 덜 붐빌 듯.', '오사카 3박4일 맛집 여행', '지민', '좋아. 그럼 점심을 우메다 쪽으로 옮겨둘게.', 1, TIMESTAMPTZ '2026-05-10T23:05:00+09:00', TIMESTAMPTZ '2026-05-10T23:05:00+09:00', NULL::timestamptz),
        ('쿠로몬시장 점심 결제', NULL, '오사카 3박4일 맛집 여행', '서준', '내 몫은 정산 때 바로 보낼게.', 0, TIMESTAMPTZ '2026-05-10T13:00:00+09:00', TIMESTAMPTZ '2026-05-10T13:00:00+09:00', NULL::timestamptz),
        ('미케비치 근처 반쎄오', NULL, '다낭 여름 휴가', '유리', '여기 다시 가도 좋을 정도로 맛있었음.', 0, TIMESTAMPTZ '2026-06-02T14:00:00+09:00', TIMESTAMPTZ '2026-06-02T14:00:00+09:00', NULL::timestamptz),
        ('미케비치 근처 반쎄오', NULL, '다낭 여름 휴가', '민서', '삭제된 예전 댓글', 0, TIMESTAMPTZ '2026-06-02T14:10:00+09:00', TIMESTAMPTZ '2026-06-02T14:20:00+09:00', TIMESTAMPTZ '2026-06-02T14:20:00+09:00')
) AS sample(post_title, parent_content, trip_title, author_display_name, content, comment_depth, created_at, updated_at, deleted_at)
JOIN posts p ON p.title = sample.post_title
JOIN trips t ON t.title = sample.trip_title
JOIN trip_participants author ON author.trip_id = t.id AND author.display_name = sample.author_display_name
LEFT JOIN post_comments parent ON parent.post_id = p.id AND parent.content = sample.parent_content
WHERE NOT EXISTS (
    SELECT 1 FROM post_comments pc WHERE pc.post_id = p.id AND pc.content = sample.content
);

DELETE FROM post_attachments
WHERE file_url LIKE 'https://images.togethertrip.local/%';

INSERT INTO post_attachments (
    post_id, attachment_type, file_url, thumbnail_url, file_size, mime_type,
    sort_order, created_at, updated_at, deleted_at
)
SELECT p.id, sample.attachment_type, sample.file_url, sample.thumbnail_url, sample.file_size, sample.mime_type,
       sample.sort_order, sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    VALUES
        ('도톤보리 첫날 동선 정리', 'IMAGE', '/uploads/post-attachments/local-osaka-dotonbori-night.jpg', '/uploads/post-attachments/local-osaka-dotonbori-night-thumb.jpg', 2481200::bigint, 'image/jpeg', 1, TIMESTAMPTZ '2026-05-10T22:45:00+09:00', TIMESTAMPTZ '2026-05-10T22:45:00+09:00', NULL::timestamptz),
        ('쿠로몬시장 점심 결제', 'IMAGE', '/uploads/post-attachments/local-osaka-kuromon-lunch-receipt.jpg', '/uploads/post-attachments/local-osaka-kuromon-lunch-receipt-thumb.jpg', 980233::bigint, 'image/jpeg', 1, TIMESTAMPTZ '2026-05-10T12:45:00+09:00', TIMESTAMPTZ '2026-05-10T12:45:00+09:00', NULL::timestamptz),
        ('미케비치 근처 반쎄오', 'VIDEO', '/uploads/post-attachments/local-danang-beach-walk.mp4', NULL, 14200122::bigint, 'video/mp4', 1, TIMESTAMPTZ '2026-06-02T20:10:00+09:00', TIMESTAMPTZ '2026-06-02T20:10:00+09:00', NULL::timestamptz),
        ('취소된 방콕 숙소 예약금', 'IMAGE', '/uploads/post-attachments/local-bangkok-cancelled-hotel.jpg', NULL, 728120::bigint, 'image/jpeg', 1, TIMESTAMPTZ '2026-03-05T18:35:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:00:00+09:00')
) AS sample(post_title, attachment_type, file_url, thumbnail_url, file_size, mime_type, sort_order, created_at, updated_at, deleted_at)
JOIN posts p ON p.title = sample.post_title
WHERE NOT EXISTS (
    SELECT 1 FROM post_attachments pa WHERE pa.post_id = p.id AND pa.file_url = sample.file_url
);

INSERT INTO transaction_events (
    transaction_id, trip_id, event_type, aggregate_version, payload, created_by_user_id,
    created_at, updated_at, deleted_at
)
SELECT tx.id, t.id, sample.event_type, sample.aggregate_version, sample.payload::jsonb, u.id,
       sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', 'CREATED', 1::bigint, '{"amount":18400,"currency":"JPY","memo":"쿠로몬시장 점심"}', '로컬 verified hana', TIMESTAMPTZ '2026-05-10T12:40:00+09:00', TIMESTAMPTZ '2026-05-10T12:40:00+09:00'),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-11T19:30:00+09:00', 'CREATED', 1::bigint, '{"amount":9200,"currency":"JPY","memo":"중복 입력"}', '로컬 verified minseo', TIMESTAMPTZ '2026-05-11T19:30:00+09:00', TIMESTAMPTZ '2026-05-11T19:30:00+09:00'),
        ('오사카 3박4일 맛집 여행', TIMESTAMPTZ '2026-05-11T19:30:00+09:00', 'VOIDED', 2::bigint, '{"reason":"중복 영수증으로 무효 처리"}', '로컬 verified minseo', TIMESTAMPTZ '2026-05-11T20:00:00+09:00', TIMESTAMPTZ '2026-05-11T20:00:00+09:00'),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', 'CREATED', 1::bigint, '{"amount":1280000,"currency":"VND","memo":"반쎄오 점심"}', '로컬 verified hana', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', TIMESTAMPTZ '2026-06-02T13:20:00+09:00'),
        ('다낭 여름 휴가', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', 'UPDATED', 1::bigint, '{"amount":48000,"currency":"KRW","memo":"마사지 팁 포함"}', '로컬 verified minseo', TIMESTAMPTZ '2026-06-03T22:05:00+09:00', TIMESTAMPTZ '2026-06-03T22:10:00+09:00')
) AS sample(trip_title, transaction_created_at, event_type, aggregate_version, payload, created_by_nickname, created_at, updated_at)
JOIN trips t ON t.title = sample.trip_title
JOIN transactions tx ON tx.trip_id = t.id AND tx.created_at = sample.transaction_created_at
JOIN users u ON u.nickname = sample.created_by_nickname
ON CONFLICT (transaction_id, aggregate_version) DO NOTHING;

INSERT INTO trip_participant_balance_summaries (
    trip_id, trip_participant_id, paid_base_amount, share_base_amount, net_base_amount,
    projection_version, created_at, updated_at, deleted_at
)
SELECT t.id, participant.id, sample.paid_base_amount, sample.share_base_amount, sample.net_base_amount,
       sample.projection_version, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', '지민', 168344.00::numeric, 98114.67::numeric, 70229.33::numeric, 6::bigint, TIMESTAMPTZ '2026-05-15T20:30:00+09:00', TIMESTAMPTZ '2026-05-15T20:30:00+09:00'),
        ('오사카 3박4일 맛집 여행', '민서', 0.00::numeric, 98114.67::numeric, -98114.67::numeric, 6::bigint, TIMESTAMPTZ '2026-05-15T20:30:00+09:00', TIMESTAMPTZ '2026-05-15T20:30:00+09:00'),
        ('오사카 3박4일 맛집 여행', '서준', 126000.00::numeric, 98114.66::numeric, 27885.34::numeric, 6::bigint, TIMESTAMPTZ '2026-05-15T20:30:00+09:00', TIMESTAMPTZ '2026-05-15T20:30:00+09:00'),
        ('다낭 여름 휴가', '지민', 69500.00::numeric, 39166.67::numeric, 30333.33::numeric, 4::bigint, TIMESTAMPTZ '2026-06-04T18:40:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00'),
        ('다낭 여름 휴가', '민서', 48000.00::numeric, 39166.67::numeric, 8833.33::numeric, 4::bigint, TIMESTAMPTZ '2026-06-04T18:40:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00'),
        ('다낭 여름 휴가', '유리', 0.00::numeric, 39166.66::numeric, -39166.66::numeric, 4::bigint, TIMESTAMPTZ '2026-06-04T18:40:00+09:00', TIMESTAMPTZ '2026-06-04T18:40:00+09:00')
) AS sample(trip_title, participant_display_name, paid_base_amount, share_base_amount, net_base_amount, projection_version, created_at, updated_at)
JOIN trips t ON t.title = sample.trip_title
JOIN trip_participants participant ON participant.trip_id = t.id AND participant.display_name = sample.participant_display_name
ON CONFLICT (trip_id, trip_participant_id) DO NOTHING;

INSERT INTO settlements (
    trip_id, status, trip_expense_version, calculation_version, base_currency,
    total_expense_amount, total_share_amount, snapshot_payload, share_token,
    confirmed_at, confirmed_by_user_id, created_at, updated_at, deleted_at
)
SELECT t.id, sample.status, sample.trip_expense_version, sample.calculation_version, sample.base_currency,
       sample.total_expense_amount, sample.total_share_amount, sample.snapshot_payload::jsonb, sample.share_token,
       sample.confirmed_at, confirmed_by.id, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', 'CONFIRMED', 6::bigint, 'settlement-v1', 'KRW', 294344.00::numeric, 294344.00::numeric, '{"tripTitle":"오사카 3박4일 맛집 여행","balances":[{"name":"지민","net":70229.33},{"name":"민서","net":-98114.67},{"name":"서준","net":27885.34}]}', 'osaka-settlement-share-2026', TIMESTAMPTZ '2026-05-15T21:00:00+09:00', '로컬 verified hana', TIMESTAMPTZ '2026-05-15T20:40:00+09:00', TIMESTAMPTZ '2026-05-15T21:00:00+09:00'),
        ('다낭 여름 휴가', 'DRAFT', 4::bigint, 'settlement-v1', 'KRW', 117500.00::numeric, 117500.00::numeric, '{"tripTitle":"다낭 여름 휴가","balances":[{"name":"지민","net":30333.33},{"name":"민서","net":8833.33},{"name":"유리","net":-39166.66}]}', NULL, NULL::timestamptz, NULL, TIMESTAMPTZ '2026-06-04T18:45:00+09:00', TIMESTAMPTZ '2026-06-04T18:45:00+09:00'),
        ('다낭 여름 휴가', 'CANCELLED', 3::bigint, 'settlement-v1', 'KRW', 69500.00::numeric, 69500.00::numeric, '{"reason":"마사지 비용 입력 전 계산본"}', NULL, NULL::timestamptz, NULL, TIMESTAMPTZ '2026-06-03T18:00:00+09:00', TIMESTAMPTZ '2026-06-04T18:00:00+09:00')
) AS sample(trip_title, status, trip_expense_version, calculation_version, base_currency, total_expense_amount, total_share_amount, snapshot_payload, share_token, confirmed_at, confirmed_by_nickname, created_at, updated_at)
JOIN trips t ON t.title = sample.trip_title
LEFT JOIN users confirmed_by ON confirmed_by.nickname = sample.confirmed_by_nickname
WHERE NOT EXISTS (
    SELECT 1
    FROM settlements s
    WHERE s.trip_id = t.id
      AND s.status = sample.status
      AND s.trip_expense_version = sample.trip_expense_version
);

INSERT INTO settlement_transfers (
    settlement_id, sender_participant_id, receiver_participant_id, amount, currency, status,
    sender_confirmed_at, receiver_confirmed_at, auto_confirmed, auto_confirm_reason, completed_at,
    created_at, updated_at, deleted_at
)
SELECT settlement.id, sender.id, receiver.id, sample.amount, 'KRW', sample.status,
       sample.sender_confirmed_at, sample.receiver_confirmed_at, sample.auto_confirmed,
       sample.auto_confirm_reason, sample.completed_at, sample.created_at, sample.updated_at, NULL
FROM (
    VALUES
        ('오사카 3박4일 맛집 여행', 'CONFIRMED', '민서', '지민', 70229.33::numeric, 'COMPLETED', TIMESTAMPTZ '2026-05-15T21:10:00+09:00', TIMESTAMPTZ '2026-05-15T21:12:00+09:00', false, NULL, TIMESTAMPTZ '2026-05-15T21:12:00+09:00', TIMESTAMPTZ '2026-05-15T21:00:00+09:00', TIMESTAMPTZ '2026-05-15T21:12:00+09:00'),
        ('오사카 3박4일 맛집 여행', 'CONFIRMED', '민서', '서준', 27885.34::numeric, 'RECEIVER_CONFIRMED', TIMESTAMPTZ '2026-05-15T21:11:00+09:00', TIMESTAMPTZ '2026-05-15T21:20:00+09:00', false, NULL, NULL::timestamptz, TIMESTAMPTZ '2026-05-15T21:00:00+09:00', TIMESTAMPTZ '2026-05-15T21:20:00+09:00'),
        ('다낭 여름 휴가', 'DRAFT', '유리', '지민', 30333.33::numeric, 'PENDING', NULL::timestamptz, NULL::timestamptz, false, NULL, NULL::timestamptz, TIMESTAMPTZ '2026-06-04T18:45:00+09:00', TIMESTAMPTZ '2026-06-04T18:45:00+09:00'),
        ('다낭 여름 휴가', 'DRAFT', '유리', '민서', 8833.33::numeric, 'SENDER_CONFIRMED', TIMESTAMPTZ '2026-06-04T19:10:00+09:00', NULL::timestamptz, false, NULL, NULL::timestamptz, TIMESTAMPTZ '2026-06-04T18:45:00+09:00', TIMESTAMPTZ '2026-06-04T19:10:00+09:00'),
        ('다낭 여름 휴가', 'CANCELLED', '유리', '지민', 23166.67::numeric, 'CANCELLED', NULL::timestamptz, NULL::timestamptz, true, '정산 스냅샷 취소', NULL::timestamptz, TIMESTAMPTZ '2026-06-03T18:00:00+09:00', TIMESTAMPTZ '2026-06-04T18:00:00+09:00')
) AS sample(trip_title, settlement_status, sender_name, receiver_name, amount, status, sender_confirmed_at, receiver_confirmed_at, auto_confirmed, auto_confirm_reason, completed_at, created_at, updated_at)
JOIN trips t ON t.title = sample.trip_title
JOIN settlements settlement ON settlement.trip_id = t.id AND settlement.status = sample.settlement_status
JOIN trip_participants sender ON sender.trip_id = t.id AND sender.display_name = sample.sender_name
JOIN trip_participants receiver ON receiver.trip_id = t.id AND receiver.display_name = sample.receiver_name
ON CONFLICT (settlement_id, sender_participant_id, receiver_participant_id) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO outbox_events (
    aggregate_type, aggregate_id, event_type, payload, status, retry_count, published_at,
    created_at, updated_at, deleted_at
)
SELECT sample.aggregate_type, sample.aggregate_id, sample.event_type, sample.payload::jsonb, sample.status,
       sample.retry_count, sample.published_at, sample.created_at, sample.updated_at, sample.deleted_at
FROM (
    SELECT 'TRIP' AS aggregate_type, t.id AS aggregate_id, 'TRIP_CREATED' AS event_type,
           jsonb_build_object('tripId', t.id, 'title', t.title)::text AS payload,
           'PUBLISHED' AS status, 0 AS retry_count, TIMESTAMPTZ '2026-05-18T20:41:00+09:00' AS published_at,
           TIMESTAMPTZ '2026-05-18T20:40:00+09:00' AS created_at, TIMESTAMPTZ '2026-05-18T20:41:00+09:00' AS updated_at, NULL::timestamptz AS deleted_at
    FROM trips t
    WHERE t.title = '다낭 여름 휴가'
    UNION ALL
    SELECT 'TRANSACTION', tx.id, 'TRANSACTION_CREATED',
           jsonb_build_object('transactionId', tx.id, 'tripId', t.id)::text,
           'PENDING', 0, NULL::timestamptz,
           TIMESTAMPTZ '2026-06-02T13:20:00+09:00', TIMESTAMPTZ '2026-06-02T13:20:00+09:00', NULL::timestamptz
    FROM trips t
    JOIN transactions tx ON tx.trip_id = t.id AND tx.created_at = TIMESTAMPTZ '2026-06-02T13:20:00+09:00'
    WHERE t.title = '다낭 여름 휴가'
    UNION ALL
    SELECT 'SETTLEMENT', s.id, 'SETTLEMENT_DRAFTED',
           jsonb_build_object('settlementId', s.id, 'tripId', t.id)::text,
           'FAILED', 3, NULL::timestamptz,
           TIMESTAMPTZ '2026-06-04T18:45:00+09:00', TIMESTAMPTZ '2026-06-04T19:00:00+09:00', NULL::timestamptz
    FROM trips t
    JOIN settlements s ON s.trip_id = t.id AND s.status = 'DRAFT'
    WHERE t.title = '다낭 여름 휴가'
    UNION ALL
    SELECT 'TRIP', t.id, 'TRIP_DELETED',
           jsonb_build_object('tripId', t.id)::text,
           'PUBLISHED', 0, TIMESTAMPTZ '2026-03-20T10:01:00+09:00',
           TIMESTAMPTZ '2026-03-20T10:00:00+09:00', TIMESTAMPTZ '2026-03-20T10:01:00+09:00', TIMESTAMPTZ '2026-03-21T00:00:00+09:00'
    FROM trips t
    WHERE t.title = '방콕 송크란 여행 취소 기록'
) AS sample
WHERE NOT EXISTS (
    SELECT 1
    FROM outbox_events oe
    WHERE oe.aggregate_type = sample.aggregate_type
      AND oe.aggregate_id = sample.aggregate_id
      AND oe.event_type = sample.event_type
      AND oe.created_at = sample.created_at
);
