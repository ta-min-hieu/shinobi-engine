-- V7 — gold (DATABASE.md §5 "Chuyển gold"/GDD §14 Gold Sink) + audit_log (DATABASE.md §2.7),
-- nền tảng bắt buộc trước khi mở EnhanceItemUseCase (GAMEPLAY.md §7).
--
-- gold nằm ở character_stat (không phải character) vì character_stat đã tách riêng do ghi/đọc tần
-- suất cao hơn (lý do gốc ở V2) — gold cũng đổi mỗi lần cường hoá/mua bán, cùng lý do đó.
ALTER TABLE character_stat ADD COLUMN gold BIGINT NOT NULL DEFAULT 0;

-- detail_json là TEXT (không JSONB) — dự án chưa có thư viện JSON (feature-inventory/CLAUDE.md
-- lý do tương tự cho drop_table_entry), nội dung ghi vào đây do server tự build từ số/enum, không
-- có input người dùng nên không cần validate JSON thật ở tầng DB.
CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES character (id),
    action VARCHAR(32) NOT NULL,
    detail_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_log_character_id ON audit_log (character_id);
