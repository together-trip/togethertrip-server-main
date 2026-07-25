-- 참여자 기반 거래 조회는 transaction_id 선두 인덱스로 역방향 탐색할 수 없다.
-- participant -> transaction 접근 경로를 지원하고 soft-deleted allocation은 인덱스에서 제외한다.
CREATE INDEX IF NOT EXISTS idx_transaction_payments_participant_transaction_active
    ON transaction_payments (trip_participant_id, transaction_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_shares_participant_transaction_active
    ON transaction_shares (trip_participant_id, transaction_id)
    WHERE deleted_at IS NULL;
