CREATE EXTENSION IF NOT EXISTS postgis;;

-- settlement_transfers 컬럼 오타 수정 (recevier -> receiver)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'settlement_transfers'
          AND column_name  = 'recevier_id'
    ) THEN
        ALTER TABLE settlement_transfers RENAME COLUMN recevier_id TO receiver_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'settlement_transfers'
          AND column_name  = 'recevier_confirmed_at'
    ) THEN
        ALTER TABLE settlement_transfers RENAME COLUMN recevier_confirmed_at TO receiver_confirmed_at;
    END IF;
END $$;;
