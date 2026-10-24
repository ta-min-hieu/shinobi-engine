-- Lô 21 phần 2 (2026-10-24): bế quan (ADR-095), thưởng online (ADR-096), hái linh thảo (ADR-098). Phân giải (ADR-097)
-- không cần bảng mới.

-- ADR-095 — mốc thoát lần cuối; rỗng = chưa từng thoát kể từ khi có cột (không tích bế quan lần đầu).
ALTER TABLE character ADD COLUMN last_logout_at TIMESTAMPTZ;

-- Quỹ bế quan: phút offline đã tích, tối đa 8 giờ; quy ra EXP lúc nhận theo cấp lúc nhận.
CREATE TABLE seclusion_bank (
    character_id   BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    banked_minutes INT         NOT NULL CHECK (banked_minutes BETWEEN 0 AND 480),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ADR-096 — giây online theo NGÀY giờ Việt Nam; claimed là bitmask các mốc đã nhận (bit 0 = 10 phút, 1 = 30, 2 = 60).
CREATE TABLE online_time (
    character_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    day          DATE   NOT NULL,
    seconds      INT    NOT NULL DEFAULT 0 CHECK (seconds >= 0),
    claimed      INT    NOT NULL DEFAULT 0 CHECK (claimed >= 0),
    PRIMARY KEY (character_id, day)
);

-- ADR-098 — bụi linh thảo trên world chính (tham số — Điều 27). Trạng thái sẵn/hồi sống trong RAM của gateway.
CREATE TABLE herb_node (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             VARCHAR(32) NOT NULL UNIQUE,
    x                REAL        NOT NULL,
    y                REAL        NOT NULL DEFAULT 0,
    item_template_id BIGINT      NOT NULL REFERENCES item_template (id),
    quantity         INT         NOT NULL CHECK (quantity > 0),
    respawn_seconds  INT         NOT NULL CHECK (respawn_seconds > 0)
);

-- Bốn bụi cạnh các trạm dọc trục (vùng safe_zone không hồi máu của V-wayposts) — chỗ người chơi vốn dừng chân.
INSERT INTO herb_node (code, x, y, item_template_id, quantity, respawn_seconds)
SELECT v.code, v.x, 0, it.id, v.qty, 180
  FROM (VALUES ('sect_garden_herb', -9.0, 1),
               ('spirit_vein_herb', 84.0, 1),
               ('dead_vein_herb', 127.0, 2),
               ('wasteland_gate_herb', 176.0, 2)) AS v (code, x, qty)
  JOIN item_template it ON it.code = 'spirit_herb'
ON CONFLICT (code) DO NOTHING;
