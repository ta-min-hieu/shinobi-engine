-- V112 — vùng PvP Hoang Vực (ADR-137, lô 25). STORY §7: Hoang Vực là vùng linh mạch chết ngoài vòng luật lệ; PvP tạm cấm
-- ở mọi World trừ Arena (2026-09-24) "cho tới khi dựng Hoang Vực — mở PvP bằng một loại zone mới". Đây là loại zone đó:
-- một dải trục X; hai người chơi CÙNG đứng trong dải (và ngoài mọi vùng an toàn) thì đánh nhau được. Dải đầu tiên là rìa
-- Hoang Vực giữa trạm cổng Hoang Vực [173.5, 178.5] và trạm trước sân khảo hạch Trúc Cơ [205.5, 210.5].
CREATE TABLE pvp_zone (
    code       VARCHAR(48) PRIMARY KEY,
    world_code VARCHAR(32) NOT NULL,
    min_x      REAL        NOT NULL,
    max_x      REAL        NOT NULL,
    CHECK (min_x < max_x)
);

INSERT INTO pvp_zone (code, world_code, min_x, max_x)
VALUES ('wasteland_rim', 'main', 179, 205)
ON CONFLICT (code) DO NOTHING;
