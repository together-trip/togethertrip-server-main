ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS phone_number_hash_version VARCHAR(30);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_verified_phone_hash
    ON users (phone_number_hash)
    WHERE phone_number_hash IS NOT NULL
      AND phone_verified_at IS NOT NULL
      AND deleted_at IS NULL;
