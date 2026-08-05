ALTER TABLE oauth_accounts
    ADD COLUMN IF NOT EXISTS encrypted_refresh_token VARCHAR(2000);
