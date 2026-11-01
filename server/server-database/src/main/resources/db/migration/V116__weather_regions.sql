-- ADR-144 (2026-11-01, lô 26) — thời tiết theo vùng, CHỈ HIỂN THỊ. Mỗi vùng là một dải trục X của world chính; trời
-- đổi theo đồng hồ, tất định: vùng "đang có" khi (phút epoch + offset_minutes) mod period_minutes < active_minutes.
-- Không cột nào chạm gameplay. Số do AI chọn — đề xuất.
CREATE TABLE weather_region (
    code            VARCHAR(32) PRIMARY KEY,
    world_code      VARCHAR(32) NOT NULL DEFAULT 'main',
    min_x           REAL        NOT NULL,
    max_x           REAL        NOT NULL,
    kind            VARCHAR(16) NOT NULL CHECK (kind IN ('SPIRIT_RAIN', 'SANDSTORM')),
    period_minutes  INT         NOT NULL CHECK (period_minutes > 0),
    active_minutes  INT         NOT NULL,
    offset_minutes  INT         NOT NULL DEFAULT 0,
    CHECK (min_x < max_x),
    CHECK (active_minutes > 0 AND active_minutes < period_minutes)
);

-- Mưa linh khí quanh tông môn (sân tông môn tới hết Linh Mạch, -40..100): 20 phút mỗi giờ.
-- Bão cát đỏ Hoang Vực sâu (trùng dải PvP wasteland_deep 307..417): 15 phút mỗi 45 phút, lệch pha 20 phút.
INSERT INTO weather_region (code, min_x, max_x, kind, period_minutes, active_minutes, offset_minutes)
VALUES ('sect_spirit_rain', -40, 100, 'SPIRIT_RAIN', 60, 20, 0),
       ('wasteland_sandstorm', 307, 417, 'SANDSTORM', 45, 15, 20)
ON CONFLICT DO NOTHING;
