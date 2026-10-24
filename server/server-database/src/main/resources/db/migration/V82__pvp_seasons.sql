-- ADR-087 — mùa Luận Kiếm 4 tuần. Một dòng mỗi mùa; mùa đang mở là dòng closed_at IS NULL (đúng một dòng).
CREATE TABLE pvp_season (
    number     INT         PRIMARY KEY CHECK (number >= 1),
    started_at TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    closed_at  TIMESTAMPTZ,
    CHECK (ends_at > started_at)
);

CREATE UNIQUE INDEX uq_pvp_season_open ON pvp_season ((closed_at IS NULL)) WHERE closed_at IS NULL;

INSERT INTO pvp_season (number, started_at, ends_at) VALUES (1, now(), now() + INTERVAL '28 days')
ON CONFLICT DO NOTHING;

-- Hàng đợi thưởng: ghi TRONG giao dịch chốt mùa, xoá dần khi thư đã gửi — server chết giữa vòng gửi thì lượt sau gửi
-- tiếp phần còn lại (khoá chống trùng của thư suy từ mùa + nhân vật nên không bao giờ gửi đôi).
CREATE TABLE pvp_season_payout (
    season       INT    NOT NULL REFERENCES pvp_season (number),
    character_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    rank         INT    NOT NULL CHECK (rank >= 1),
    rating       INT    NOT NULL,
    PRIMARY KEY (season, character_id)
);

-- Thưởng theo hạng Elo lúc chốt — bậc ĐẦU TIÊN (theo rank_from) phủ hạng là bậc được trao. THAM SỐ (Điều 27).
CREATE TABLE pvp_season_reward (
    rank_from        INT    PRIMARY KEY CHECK (rank_from >= 1),
    rank_to          INT    NOT NULL,
    gold             BIGINT NOT NULL CHECK (gold >= 0),
    item_template_id BIGINT REFERENCES item_template (id),
    quantity         INT    NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    CHECK (rank_to >= rank_from)
);

INSERT INTO pvp_season_reward (rank_from, rank_to, gold, item_template_id, quantity)
SELECT v.rank_from, v.rank_to, v.gold, it.id, v.qty
  FROM (VALUES (1,   1,       10000, 'spirit_beast_pill', 1),
               (2,   3,       6000,  'spirit_jade_fire',  1),
               (4,   10,      3000,  NULL,                0),
               (11,  1000000, 500,   NULL,                0)) AS v (rank_from, rank_to, gold, item_code, qty)
  LEFT JOIN item_template it ON it.code = v.item_code
ON CONFLICT DO NOTHING;
