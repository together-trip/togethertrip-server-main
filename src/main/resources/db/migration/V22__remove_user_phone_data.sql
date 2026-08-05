DROP INDEX IF EXISTS uk_users_verified_phone_hash;

ALTER TABLE users
    DROP COLUMN IF EXISTS phone_number,
    DROP COLUMN IF EXISTS phone_number_encrypted,
    DROP COLUMN IF EXISTS phone_number_encryption_version,
    DROP COLUMN IF EXISTS phone_number_masked,
    DROP COLUMN IF EXISTS phone_number_hash,
    DROP COLUMN IF EXISTS phone_number_hash_version,
    DROP COLUMN IF EXISTS phone_verified_at;
