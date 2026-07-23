CREATE INDEX IF NOT EXISTS idx_exchange_rates_base_date_currency
    ON exchange_rates (base_currency, rate_date DESC, target_currency)
    WHERE deleted_at IS NULL;
