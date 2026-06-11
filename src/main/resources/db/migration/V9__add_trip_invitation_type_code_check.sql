ALTER TABLE trip_invitations
    DROP CONSTRAINT IF EXISTS trip_invitations_type_code_check,
    ADD CONSTRAINT trip_invitations_type_code_check
        CHECK (
            (invitation_type = 'CODE' AND code IS NOT NULL)
            OR (invitation_type = 'LINK' AND code IS NULL)
        );
