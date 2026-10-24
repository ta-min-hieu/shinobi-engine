-- ADR-090 — Tranh đoạt Linh Mạch (bản đầu). Mỗi tuần một khung giờ: bang có NHIỀU người đứng trong vùng nhất được 1
-- điểm mỗi 10 giây; khung đóng ⇒ bang nhiều điểm nhất giữ danh hiệu Chủ Linh Mạch 7 ngày + thành viên nhận thư thưởng.

-- THAM SỐ (Điều 27). Lịch theo UTC: weekday ISO 1–7, start_minute trong ngày. Hai cột holder_* là trạng thái — ai đang
-- giữ, tới khi nào; bang giải tán ⇒ trả lại.
CREATE TABLE spirit_vein (
    code                    VARCHAR(32)  PRIMARY KEY,
    name_key                VARCHAR(64)  NOT NULL,
    min_x                   REAL         NOT NULL,
    max_x                   REAL         NOT NULL,
    weekday                 INT          NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_minute            INT          NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    duration_minutes        INT          NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    reward_gold             BIGINT       NOT NULL CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    holder_guild_id         BIGINT       REFERENCES guild (id) ON DELETE SET NULL,
    held_until              TIMESTAMPTZ,
    CHECK (min_x <= max_x)
);

-- Điểm từng bang trong một khung (khung nhận diện bằng lúc bắt đầu). reached_at = lần ghi điểm gần nhất — hoà điểm
-- thì bang đạt tới trước thắng.
CREATE TABLE spirit_vein_score (
    vein_code    VARCHAR(32) NOT NULL REFERENCES spirit_vein (code),
    window_start TIMESTAMPTZ NOT NULL,
    guild_id     BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    points       INT         NOT NULL CHECK (points >= 0),
    reached_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vein_code, window_start, guild_id)
);

-- Một dòng mỗi khung đã chốt — khoá chính làm phép chốt chỉ chạy MỘT lần dù nhiều luồng/khởi động lại.
CREATE TABLE spirit_vein_settlement (
    vein_code       VARCHAR(32) NOT NULL REFERENCES spirit_vein (code),
    window_start    TIMESTAMPTZ NOT NULL,
    winner_guild_id BIGINT,
    settled_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vein_code, window_start)
);

-- Hàng đợi thư thưởng, như pvp_season_payout (ADR-087): ghi trong giao dịch chốt, xoá dần khi thư đã gửi.
CREATE TABLE spirit_vein_payout (
    vein_code    VARCHAR(32)  NOT NULL REFERENCES spirit_vein (code),
    window_start TIMESTAMPTZ  NOT NULL,
    character_id BIGINT       NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    guild_name   VARCHAR(64)  NOT NULL,
    PRIMARY KEY (vein_code, window_start, character_id)
);

-- Linh Mạch đầu tiên: ngay phía đông vùng an toàn tông môn (sect_grounds kết thúc ở x = 5). Thứ Bảy 13:00 UTC
-- (20:00 giờ Việt Nam), 30 phút. Số thưởng là PLACEHOLDER cân bằng.
INSERT INTO spirit_vein (code, name_key, min_x, max_x, weekday, start_minute, duration_minutes,
                         reward_gold, reward_item_template_id, reward_quantity)
SELECT 'azure_cloud_vein', 'spirit_vein.azure_cloud_vein', 20, 32, 6, 780, 30, 3000, it.id, 1
  FROM (SELECT 1) AS one
  LEFT JOIN item_template it ON it.code = 'spirit_beast_pill'
ON CONFLICT DO NOTHING;
