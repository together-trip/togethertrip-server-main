ALTER TABLE users
    DROP COLUMN IF EXISTS email;

ALTER TABLE oauth_accounts
    DROP COLUMN IF EXISTS email;
