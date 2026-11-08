-- ADR-256 (2026-11-08) — lịch hoạt động bang: bang chủ / phó hẹn giờ, cả bang được nhắc trước 10 phút (cờ reminded).
CREATE TABLE guild_event (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id   BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    created_by BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    title      VARCHAR(60) NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    reminded   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guild_event_guild ON guild_event (guild_id, starts_at);
CREATE INDEX idx_guild_event_due ON guild_event (starts_at) WHERE NOT reminded;
