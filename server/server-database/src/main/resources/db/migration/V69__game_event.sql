-- ADR-067 — sự kiện nhân đôi EXP/rơi đồ do ADMIN bật bằng /gm event. Một dòng mỗi loại; hết hạn thì dòng
-- vẫn nằm đó (server lọc theo đồng hồ) cho tới lần bật/tắt sau. Gắn dòng audit GM đã bật nó.
CREATE TABLE game_event (
    kind             VARCHAR(16) PRIMARY KEY CHECK (kind IN ('EXP', 'DROP')),
    multiplier       INT         NOT NULL CHECK (multiplier BETWEEN 2 AND 5),
    ends_at          TIMESTAMPTZ NOT NULL,
    gm_audit_log_id  BIGINT      NOT NULL REFERENCES gm_audit_log (id)
);
