ALTER TABLE trip_invitations
    ADD COLUMN IF NOT EXISTS code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS invitation_type VARCHAR(20) NOT NULL DEFAULT 'LINK';

ALTER TABLE trip_invitations
    DROP CONSTRAINT IF EXISTS trip_invitations_invitation_type_check,
    ADD CONSTRAINT trip_invitations_invitation_type_check
        CHECK (invitation_type IN ('CODE', 'LINK'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_invitations_code
    ON trip_invitations (code)
    WHERE code IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_participants_trip_user_active
    ON trip_participants (trip_id, user_id)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL AND participant_status = 'ACTIVE';
