-- ADR-248 (2026-11-08) — bình chọn trong bang. Mỗi bang chỉ có MỘT cuộc mở: tạo cuộc mới thì đóng cuộc cũ (closes_at).
CREATE TABLE guild_poll (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id   BIGINT        NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    created_by BIGINT        NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    question   VARCHAR(80)   NOT NULL,
    options    VARCHAR(32)[] NOT NULL CHECK (cardinality(options) BETWEEN 2 AND 4),
    created_at TIMESTAMPTZ   NOT NULL,
    closes_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_guild_poll_guild ON guild_poll (guild_id, id DESC);

-- Một phiếu mỗi (cuộc, người); đổi phiếu = cập nhật dòng.
CREATE TABLE guild_poll_vote (
    poll_id      BIGINT   NOT NULL REFERENCES guild_poll (id) ON DELETE CASCADE,
    character_id BIGINT   NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    option_index SMALLINT NOT NULL CHECK (option_index BETWEEN 0 AND 3),
    PRIMARY KEY (poll_id, character_id)
);
