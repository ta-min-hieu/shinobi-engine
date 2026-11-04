-- ADR-169 (2026-11-04) — Sổ vàng cá nhân. Mỗi lần vàng đổi (mọi đường đều đi GoldLedger) ghi một dòng TRONG CÙNG
-- transaction với lần đổi đó: rollback thì dòng sổ cũng biến mất. Giữ 30 ngày (RetentionJanitor).
CREATE TABLE gold_ledger_entry (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT      NOT NULL,
    delta        BIGINT      NOT NULL,
    balance      BIGINT      NOT NULL CHECK (balance >= 0),
    reason_key   VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gold_ledger_entry_character ON gold_ledger_entry (character_id, id DESC);
CREATE INDEX idx_gold_ledger_entry_created ON gold_ledger_entry (created_at);
