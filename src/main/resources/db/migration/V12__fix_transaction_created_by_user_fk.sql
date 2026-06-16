-- transactions.created_by_user_id stores the user who created the transaction.
-- Some local schemas may still have an obsolete FK to trip_participants(id).
-- Remove only the obsolete FK and keep/add the intended FK to users(id).

DO $$
DECLARE
    obsolete_constraint_name text;
BEGIN
    FOR obsolete_constraint_name IN
        SELECT constraint_info.conname
        FROM pg_constraint constraint_info
        JOIN pg_class table_info
            ON table_info.oid = constraint_info.conrelid
        JOIN pg_attribute column_info
            ON column_info.attrelid = constraint_info.conrelid
           AND column_info.attnum = ANY (constraint_info.conkey)
        WHERE constraint_info.contype = 'f'
          AND table_info.relname = 'transactions'
          AND column_info.attname = 'created_by_user_id'
          AND constraint_info.confrelid = 'trip_participants'::regclass
    LOOP
        EXECUTE format(
            'ALTER TABLE transactions DROP CONSTRAINT %I',
            obsolete_constraint_name
        );
    END LOOP;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        JOIN pg_class table_info
            ON table_info.oid = constraint_info.conrelid
        JOIN pg_attribute column_info
            ON column_info.attrelid = constraint_info.conrelid
           AND column_info.attnum = ANY (constraint_info.conkey)
        WHERE constraint_info.contype = 'f'
          AND table_info.relname = 'transactions'
          AND column_info.attname = 'created_by_user_id'
          AND constraint_info.confrelid = 'users'::regclass
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_created_by_user_id_users
            FOREIGN KEY (created_by_user_id) REFERENCES users (id);
    END IF;
END $$;
