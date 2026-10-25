-- ADR-102 — Bang chiến. Bang chủ tuyên chiến một bang khác (tốn vàng); 5 phút sau hai bang được đánh nhau 60 phút ngoài
-- vùng an toàn trên world chính, mỗi lần hạ một thành viên đối địch +1 điểm; hết giờ chốt nguyên tử một lần, mọi thành
-- viên bang thắng nhận thư.

-- guild_a = bang tuyên chiến, guild_b = bang bị tuyên. settled_at NULL = chưa chốt (đang chờ hoặc đang đánh).
-- winner_guild_id NULL sau khi chốt = hoà. Bang giải tán ⇒ xoá luôn trận (không còn ai để thưởng).
CREATE TABLE guild_war (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_a         BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    guild_b         BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    declared_by     BIGINT      REFERENCES character (id) ON DELETE SET NULL,
    declared_at     TIMESTAMPTZ NOT NULL,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    kills_a         INT         NOT NULL DEFAULT 0 CHECK (kills_a >= 0),
    kills_b         INT         NOT NULL DEFAULT 0 CHECK (kills_b >= 0),
    settled_at      TIMESTAMPTZ,
    winner_guild_id BIGINT,
    CHECK (guild_a <> guild_b),
    CHECK (starts_at >= declared_at AND ends_at > starts_at)
);

CREATE INDEX guild_war_unsettled_idx ON guild_war (ends_at) WHERE settled_at IS NULL;
CREATE INDEX guild_war_guild_a_idx ON guild_war (guild_a, ends_at);
CREATE INDEX guild_war_guild_b_idx ON guild_war (guild_b, ends_at);

-- Thư thưởng còn chờ gửi — ghi trong giao dịch chốt, xoá khi thư đã nằm trong hộp (cùng khuôn spirit_vein_payout).
CREATE TABLE guild_war_payout (
    war_id       BIGINT      NOT NULL REFERENCES guild_war (id) ON DELETE CASCADE,
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    guild_name   VARCHAR(64) NOT NULL,
    PRIMARY KEY (war_id, character_id)
);
