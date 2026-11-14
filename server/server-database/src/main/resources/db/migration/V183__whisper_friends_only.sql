-- ADR-402 (lô 59) — chỉ bạn bè (và người cùng bang) được nhắn riêng. Có dòng = đang bật.
CREATE TABLE whisper_friends_only (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    since TIMESTAMPTZ NOT NULL DEFAULT now()
);
