ALTER TABLE user_agreements
    ADD COLUMN IF NOT EXISTS term_version VARCHAR(20);

UPDATE user_agreements
SET agreement_type = CASE agreement_type
    WHEN 'LOCATION_TERMS' THEN 'LOCATION_INFO_TERMS'
    WHEN 'MARKETING' THEN 'MARKETING_CONSENT'
    ELSE agreement_type
END;

UPDATE user_agreements
SET deleted_at = COALESCE(deleted_at, now()),
    updated_at = now()
WHERE agreement_type NOT IN (
    'SERVICE_TERMS',
    'PRIVACY_POLICY',
    'LOCATION_INFO_TERMS',
    'MARKETING_CONSENT'
);

UPDATE user_agreements
SET term_version = '2026-06-18'
WHERE term_version IS NULL;

ALTER TABLE user_agreements
    ALTER COLUMN term_version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_agreements_user_type
    ON user_agreements (user_id, agreement_type)
    WHERE deleted_at IS NULL;
