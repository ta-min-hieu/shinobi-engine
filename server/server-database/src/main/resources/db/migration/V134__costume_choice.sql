-- ADR-166 (2026-11-03) — Huyễn hình trang phục. Người chơi chọn HIỂN THỊ một bậc trang phục đã mở (bậc theo cảnh giới
-- hiện tại hoặc thấp hơn — ADR-132). Không có dòng = tự theo cảnh giới. Bậc chọn cao hơn bậc đã mở không bao giờ hiện
-- (server lấy min), nên tụt cấp (trùng sinh) không làm lộ bậc chưa mở.
CREATE TABLE character_costume_choice (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id),
    costume_tier SMALLINT    NOT NULL CHECK (costume_tier BETWEEN 1 AND 3),
    chosen_at    TIMESTAMPTZ NOT NULL
);
