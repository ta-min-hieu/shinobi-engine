-- ADR-230 (2026-11-08) — khoe thành tựu vào bang: mở thành tựu thì báo một dòng cho cả bang; mặc định BẬT, tắt được.
ALTER TABLE character ADD COLUMN share_achievements BOOLEAN NOT NULL DEFAULT TRUE;

-- ADR-227 (2026-11-08) — gói quà tân thủ: đánh dấu đã gửi để mỗi nhân vật nhận đúng một lần (kể cả khi tạo lại lệnh).
ALTER TABLE character ADD COLUMN starter_gift_sent BOOLEAN NOT NULL DEFAULT FALSE;
